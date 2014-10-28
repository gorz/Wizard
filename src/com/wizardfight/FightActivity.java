package com.wizardfight;

import android.view.*;

import com.wizardfight.recognition.Recognizer;
import com.wizardfight.views.*;

import java.util.ArrayList;

import com.wizardfight.components.Vector3d;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * This is the main Activity that displays the current chat session.
 */
public class FightActivity extends Activity {

	static final int PLAYER_HP = 200;
	static final int PLAYER_MANA = 100;
	// Debugging
	private int mMyCounter;
	static final String TAG = "Wizard Fight";
	static final boolean D = false;
	// is activity running
	private boolean mIsRunning;
	// States of players
	private SelfState mSelfState;
	private EnemyState mEnemyState;
	private boolean mAreMessagesBlocked;

	// Message types sent from the BluetoothChatService Handler
	enum AppMessage {
		MESSAGE_STATE_CHANGE, MESSAGE_READ, MESSAGE_WRITE, MESSAGE_DEVICE_NAME, MESSAGE_TOAST, MESSAGE_COUNTDOWN_END, MESSAGE_CONNECTION_FAIL, MESSAGE_FROM_SELF, MESSAGE_SELF_DEATH, MESSAGE_FROM_ENEMY, MESSAGE_MANA_REGEN
	}

	// Key names received from the BluetoothChatService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";
	// Layout Views
	private Countdown mCountdown;
	private SelfGUI mSelfGUI;
	private EnemyGUI mEnemyGUI;
    // Objects referred to accelerometer
	private SensorManager mSensorManager = null;
	private Sensor mAccelerometer = null;
	// Accelerator Thread link
	private SensorAndSoundThread mSensorAndSoundThread = null;
	// Last touch action code
	private int mLastTouchAction;

	private boolean mIsInCast = false;
	private boolean mIsCastAbilityBlocked = false;
	
	//private Dialog mClientWaitingDialog;
    FightEndDialog mFightEndDialog;
	// test mode dialog with spell names

	private FightBackground mBgImage;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (D)
			Log.e(TAG, "+++ ON CREATE +++");
		setContentView(R.layout.fight);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		// add countdown view to the top
		LayoutInflater inflater = getLayoutInflater();
		View countdownView = inflater.inflate(R.layout.countdown, null);
		mCountdown = new Countdown(this, countdownView, mHandler);
		getWindow().addContentView(
				countdownView,
				new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
						ViewGroup.LayoutParams.FILL_PARENT));
		// Init recognition resources
		SharedPreferences appPrefs = PreferenceManager
				.getDefaultSharedPreferences(getBaseContext());
		String rType = appPrefs.getString("recognition_type", "");
		Recognizer.init(getResources(), rType);

		// Init on touch listener
		LinearLayout rootLayout = (LinearLayout) findViewById(R.id.fight_layout_root);
		rootLayout.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (mAreMessagesBlocked || mIsCastAbilityBlocked)
					return true;
				int action = event.getAction();
				if (action == MotionEvent.ACTION_UP
						|| action == MotionEvent.ACTION_DOWN) {
					if (mLastTouchAction == action) {
						return true;
					}
					if(action == MotionEvent.ACTION_DOWN) {
						mBgImage.toBright();
					} else {
						mBgImage.toDark();
					}
					buttonClick();
					mLastTouchAction = action;
				}
				return false;
			}	
		});

		// Get sensors
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mAccelerometer = mSensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		// Initialize GUI and logic
		setupApp();
		// Initialize end dialog object
		mBgImage = (FightBackground) findViewById(R.id.fight_background);
	}

	@Override
	public void onStart() {
		super.onStart();
		if (D)
			Log.e(TAG, "++ ON START ++");
	}

	@Override
	public synchronized void onResume() {
		super.onResume();
		if (D)
			Log.e(TAG, "+ ON RESUME +");
		mIsRunning = true;
		
		mLastTouchAction = MotionEvent.ACTION_UP;
		mBgImage.darkenImage();
		
		if (mFightEndDialog.isNeedToShow()) {
			mFightEndDialog.show();
		}

		mSensorAndSoundThread = new SensorAndSoundThread(this, mSensorManager,
				mAccelerometer);
		mSensorAndSoundThread.start();
		if (D)
			Log.e(TAG, "accelerator ran");
	}

	@Override
	public synchronized void onPause() {
		super.onPause();
		mIsRunning = false;
		if (D)
			Log.e(TAG, "- ON PAUSE -");
		stopSensorAndSound();
	}

	@Override
	public void onStop() {
		super.onStop();
		if (D)
			Log.e(TAG, "-- ON STOP --");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// remove all messages from handler
		mHandler.removeCallbacksAndMessages(null);
		Log.e(TAG, "--- ON DESTROY ---");
	}

	private void stopSensorAndSound() {
		Log.e("Wizard Fight", "stop sensor and sound called");
		// stop cast if its started
		if (mIsInCast) {
			mIsInCast = false;
			mIsCastAbilityBlocked = false;
		}

		if (mSensorAndSoundThread != null) {
			// stop cast
			mSensorAndSoundThread.stopGettingData();
			// unregister accelerator listener and end stop event loop
			if (D)
				Log.e(TAG, "accelerator thread try to stop loop");
			mSensorAndSoundThread.stopLoop();
			mSensorAndSoundThread = null;
		}
	}

	void setupApp() {
		if (D)
			Log.d(TAG, "setupApp()");
		// for debugging
		mMyCounter = 0;
		// Create players states
		mEnemyState = new EnemyState(PLAYER_HP, PLAYER_MANA, null);
		mSelfState = new SelfState(PLAYER_HP, PLAYER_MANA, mEnemyState);
		// Initialize players UI
		mSelfGUI = new SelfGUI(this, PLAYER_HP, PLAYER_MANA);
		mEnemyGUI = new EnemyGUI(this, PLAYER_HP, PLAYER_MANA);
		// Drop flags
		mAreMessagesBlocked = true;
		// Last touch value
		mLastTouchAction = MotionEvent.ACTION_UP;
		// Start mana regeneration
		mHandler.removeMessages(AppMessage.MESSAGE_MANA_REGEN.ordinal());
		mHandler.obtainMessage(AppMessage.MESSAGE_MANA_REGEN.ordinal(), null)
				.sendToTarget();
	}

	void sendFightMessage(FightMessage fMessage) {
		// always send own health and mana
		fMessage.health = mSelfState.getHealth();
		fMessage.mana = mSelfState.getMana();
	}

	void startFight() {
		// start countdown
		if (D)
			Log.e(TAG, "before start countdown");

		mCountdown.startCountdown();
		if (D)
			Log.e(TAG, "after start countdown");
		if (D)
			Log.e(TAG, "accelerator thread all stuff called");
	}

	// The Handler that gets information back from the BluetoothChatService
	final Handler mHandler = new Handler() {
		/**
		 * Sends a message.
		 * 
		 * @param msg
		 *            A string of text to send.
		 */

		@Override
		public void handleMessage(Message msg) {
			AppMessage appMsg = AppMessage.values()[msg.what];
			switch (appMsg) {
			case MESSAGE_STATE_CHANGE:
				if (D)
					Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch (msg.arg1) {
				case BluetoothService.STATE_CONNECTED:
					// start fight
					startFight();
					break;
				case BluetoothService.STATE_NONE:
					break;
				}
				break;
			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
                String mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				Toast.makeText(getApplicationContext(),
						"Connected to " + mConnectedDeviceName,
						Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
				break;
			case MESSAGE_COUNTDOWN_END:
				mAreMessagesBlocked = false;
				break;
			case MESSAGE_CONNECTION_FAIL:
				Toast.makeText(getApplicationContext(),
						msg.getData().getInt(TOAST), Toast.LENGTH_SHORT).show();
				finish();
				break;
			case MESSAGE_FROM_SELF:
				if (mAreMessagesBlocked)
					return;
				FightMessage selfMsg = (FightMessage) msg.obj;
				handleSelfMessage(selfMsg);
				break;
			case MESSAGE_SELF_DEATH:
				FightMessage selfDeath = new FightMessage(Target.ENEMY,
						FightAction.FIGHT_END);
				sendFightMessage(selfDeath);
				break;
			case MESSAGE_FROM_ENEMY:
				byte[] recvBytes = (byte[]) msg.obj;
				FightMessage enemyMsg = FightMessage.fromBytes(recvBytes);

				switch (enemyMsg.action) {
				case ENEMY_READY:
					handleEnemyReadyMessage();
					break;
				case FIGHT_START:
					startFight();
					break;
				case FIGHT_END:
					Log.e(TAG, "MESSAGE FIGHT END!!!");
					finishFight(Target.SELF);
					break;
				default:
					if (mAreMessagesBlocked)
						return;
					handleEnemyMessage(enemyMsg);
				}
				break;
			case MESSAGE_MANA_REGEN:
				mSelfState.manaTick();
				mSelfGUI.getManaBar().setValue(mSelfState.getMana());
				// inform enemy about new mana
				FightMessage fMsg = new FightMessage(Target.ENEMY,
						FightAction.NEW_HP_OR_MANA, Shape.NONE.ordinal());
				sendFightMessage(fMsg);
				// send next tick after 2 sec
				Message msgManaReg = this.obtainMessage(
						AppMessage.MESSAGE_MANA_REGEN.ordinal(), 0, 0, null);
				this.sendMessageDelayed(msgManaReg, 2000);
				break;
			default:
				if (D)
					Log.e("Wizard Fight", "Unknown message");
				break;
			}
		}

		private void handleSelfMessage(FightMessage selfMsg) {
			Shape sendShape = FightMessage.getShapeFromMessage(selfMsg);
			if (sendShape != Shape.NONE) {
				mIsCastAbilityBlocked = false;
			}
			// mSelfGUI.log("self msg : " + selfMsg + " " + (mMyCounter++));
			Log.e(TAG, "self msg : " + selfMsg + " " + mMyCounter);
			// request mana for spell
			boolean canBeCasted = mSelfState.requestSpell(selfMsg);
			if (!canBeCasted) {
				return;
			}
			// play shape sound. condition is needed when game is suddenly
			// paused after spell
			if (mSensorAndSoundThread != null) {
				mSensorAndSoundThread.playShapeSound(sendShape);
			}

			mSelfGUI.getManaBar().setValue(mSelfState.mana);

			if (selfMsg.target == Target.SELF) {
				// self influence to self
				handleMessageToSelf(selfMsg);
			} else {
				// self influence to enemy
				// tell enemy : target is he
				selfMsg.target = Target.SELF;
				sendFightMessage(selfMsg);
			}
			// draw casted shape
			if (sendShape != Shape.NONE) {
				mSelfGUI.getSpellPicture().setShape(sendShape);
			}
		}

		private void handleEnemyMessage(FightMessage enemyMsg) {

			Shape recvShape = FightMessage.getShapeFromMessage(enemyMsg);

			// refresh enemy health and mana (every enemy message contains it)
			mEnemyState.setHealthAndMana(enemyMsg.health, enemyMsg.mana);
			Log.e(TAG, "enemy msg: " + enemyMsg + " " + mMyCounter);
			if (enemyMsg.target == Target.SELF) {
				handleMessageToSelf(enemyMsg);
			} else {
				// Enemy influence to himself
				mEnemyState.handleSpell(enemyMsg);

				if (mEnemyState.getRemovedBuff() != null) {
					// remove buff from enemy GUI
					mEnemyGUI.getBuffPanel().removeBuff(
							mEnemyState.getRemovedBuff());
				}

				if (mEnemyState.getAddedBuff() != null) {
					// add buff to enemy GUI
					mEnemyGUI.getBuffPanel()
							.addBuff(mEnemyState.getAddedBuff());
				}
			}

			// refresh enemy
			if (FightMessage.isSpellCreatedByEnemy(enemyMsg)) {
				mEnemyGUI.getSpellPicture().setShape(recvShape);
			}
			mEnemyGUI.getHealthBar().setValue(mEnemyState.health);
			mEnemyGUI.getManaBar().setValue(mEnemyState.mana);
		}

		private void handleMessageToSelf(FightMessage fMessage) {
			FightMessage sendMsg;
			// Enemy influence to player
			mSelfState.handleSpell(fMessage);
			if (mSelfState.health == 0) {
				finishFight(Target.ENEMY);
				return;
			}

			Buff addedBuff = mSelfState.getAddedBuff();
			Buff removedBuff = mSelfState.getRemovedBuff();
			Buff refreshedBuff = mSelfState.getRefreshedBuff();
			Shape spellShape = mSelfState.getSpellShape();

			if (removedBuff != null) {
				// buff was removed after spell,
				// send message about buff loss to enemy
				sendMsg = new FightMessage(Target.ENEMY, FightAction.BUFF_OFF,
						removedBuff.ordinal());
				sendFightMessage(sendMsg);
				// remove buff from panel
				mSelfGUI.getBuffPanel().removeBuff(removedBuff);
				if (mSelfState.isBuffRemovedByEnemy()) {
					mSensorAndSoundThread.playBuffSound(removedBuff);
				}
			}

			if (addedBuff != null) {
				// buff added to player after spell (for example
				// DoT, HoT, or shield),
				// send message about enemy buff success
				sendMsg = new FightMessage(Target.ENEMY, FightAction.BUFF_ON,
						addedBuff.ordinal());
				sendFightMessage(sendMsg);
				// add buff to panel
				mSelfGUI.getBuffPanel().addBuff(addedBuff);
			}

			if (addedBuff != null || refreshedBuff != null) {
				// send message of the buff tick
				if (addedBuff != null)
					refreshedBuff = addedBuff;
				FightMessage fm = new FightMessage(Target.SELF,
						FightAction.BUFF_TICK, refreshedBuff.ordinal());
				Message buffTickMsg = this.obtainMessage(
						AppMessage.MESSAGE_FROM_SELF.ordinal(), fm);
				this.sendMessageDelayed(buffTickMsg,
						refreshedBuff.getDuration());
			}

			if (addedBuff == null && removedBuff == null) {
				// nothing with buffs => just send self hp and mana to enemy
				sendMsg = new FightMessage(Target.ENEMY,
						FightAction.NEW_HP_OR_MANA, spellShape.ordinal());
				sendFightMessage(sendMsg);
			}

			mSelfGUI.getHealthBar().setValue(mSelfState.health);
			mSelfGUI.getManaBar().setValue(mSelfState.mana);
		}

	};

	void handleEnemyReadyMessage() {
		// BETTER DO THIS VIA INTERFACE!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
	}
	
	private void finishFight(Target winner) {
		Log.e(TAG, "FINISH FIGHT");
		mAreMessagesBlocked = true;
		// stop sensor thread work
		stopSensorAndSound();
		// Initialize new accelerator thread
		mSensorAndSoundThread = new SensorAndSoundThread(this, mSensorManager,
				mAccelerometer);
		mSensorAndSoundThread.start();
		// set GUI to initial state
		mBgImage.darkenImage();
		mSelfGUI.clear();
		mEnemyGUI.clear();
		// Recreate objects
		setupApp();

		String message;
		if (winner == Target.SELF) {
			message = "You win!";
		} else {
			// we must inform enemy about loss
			mHandler.obtainMessage(AppMessage.MESSAGE_SELF_DEATH.ordinal())
					.sendToTarget();
			message = "You lose!";
		}

		mFightEndDialog.init(message);
		// consider the dialog call while activity is not running
		if (mIsRunning) {
			mFightEndDialog.show();
		} else {
			mFightEndDialog.setNeedToShow(true);
		}
	}

	void buttonClick() {
		if (mIsCastAbilityBlocked)
			return;

		if (!mIsInCast) {
			mSensorAndSoundThread.startGettingData();
			mIsInCast = true;

		} else {
			mIsCastAbilityBlocked = true;

			ArrayList<Vector3d> records = mSensorAndSoundThread
					.stopAndGetResult();
			mIsInCast = false;

			if (records.size() > 10) {
				new RecognitionThread(mHandler, records).start();
			} else {
				// if shord record - don`t recognize & unblock
				mIsCastAbilityBlocked = false;
			}
		}
	}

	class FightEndDialog implements DialogInterface.OnClickListener {
		AlertDialog mmDialog;
		boolean mmIsNeedToShow;

		public void init(String message) {
			Log.e(TAG, "INIT FIGHT");
			mmIsNeedToShow = false;
			mmDialog.setTitle("Fight ended");
			mmDialog.setMessage(message);
			mmDialog.setButton("Restart", this);
			mmDialog.setButton2("Exit", this);
			mmDialog.setCancelable(false);
		}

		public void setNeedToShow(boolean isNeed) {
			mmIsNeedToShow = isNeed;
		}

		public boolean isNeedToShow() {
			return mmIsNeedToShow;
		}

		public void show() {
			mmDialog.show();
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			// TODO Auto-generated method stub
			
		}
	}
}