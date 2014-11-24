package com.wizardfight.views;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.wizardfight.R;

import java.util.ArrayList;

public class WizardDial extends RelativeLayout {
    private final TextView textView;
    private final CancelButton nextButton;
    private final Animation animFadeOut;
    private final Animation animFadeIn;
    private ArrayList<WizardDialContent> content=new ArrayList<WizardDialContent>();
    private int count = -1;
    private final ManaIndicator mi;
    private final HealthIndicator hi;
    private final RelativeLayout r;
    private final int popupId =Integer.MAX_VALUE;

    public WizardDial(Context context) {
        super(context);
        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        textView = new TextView(context);
        textView.setBackgroundResource(R.drawable.wd3);
        textView.setLayoutParams(params);
        textView.setTextSize(metrics.widthPixels/20  / (metrics.xdpi/160));

        textView.setTextColor(Color.rgb(92,67,51));
        textView.setId(popupId);
        nextButton = new CancelButton(context);
        nextButton.setBackgroundResource(R.drawable.next);
        double size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());
        params = new LayoutParams(
                (int)(size*40), (int)(size*40)
        );
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        params.setMargins(0,(int)(80*size/3), (int)(20*size), 0);
        params.addRule(RelativeLayout.ALIGN_TOP, popupId);
        nextButton.setLayoutParams(params);
        nextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                goNext();
            }
        });
        addView(textView);
        addView(nextButton);
        setBackgroundColor(Color.argb(200, 0, 0, 0));


        r=new RelativeLayout(context);
        addView(r);
        params = new LayoutParams(
                (int)(196*size), (int)(36*size)
        );
        params.setMargins((int)(2*size),(int)(25*size),0,0);
        hi=new HealthIndicator(context,null);
        hi.setLayoutParams(params);
        hi.setValue(100);
        r.addView(hi);
        params = new LayoutParams(
                (int)(196*size), (int)(36*size)
        );
        params.setMargins((int)(2*size),(int)(63*size),0,0);
        mi=new ManaIndicator(context,null);
        mi.setLayoutParams(params);
        mi.setValue(200);
        r.addView(mi);
        params = new LayoutParams(
                (int)(310*size), (int)(176*size)
        );
        params.setMargins(0,0,0,0);
        ImageView hmb=new ImageView(context);
        hmb.setImageResource(R.drawable.hmbar_m);
        hmb.setLayoutParams(params);
        r.addView(hmb);
        r.setVisibility(INVISIBLE);




        animFadeOut = AnimationUtils
                .loadAnimation(getContext(), R.anim.fade_out);
        animFadeIn = AnimationUtils
                .loadAnimation(getContext(), R.anim.fade_in);
        setVisibility(INVISIBLE);
    }

    public void show() {
        if (getVisibility() == INVISIBLE) {
            setContent(content);
            setEnabled(true);
            setVisibility(VISIBLE);
            startAnimation(animFadeIn);
        }
    }
    public void showQuick() {
        if (getVisibility() == INVISIBLE) {
            setContent(content);
            setEnabled(true);
            setVisibility(VISIBLE);
        }
    }

    public void setContent(ArrayList<WizardDialContent> content){
        this.content = content;
        count = -1;
        goNext();
    }
    public void goNext() {
        setCanNext(true);
        count++;
        r.setVisibility(INVISIBLE);
        if (count >= content.size()) {
            ((WizardDialDelegate) getContext()).onWizardDialClose();
            setCanNext(false);
            close();
        } else {
            mHandler.removeCallbacks(mTick);
            if(content.get(count).isUi()) {
                hi.setMaxValue(100);
                mi.setMaxValue(100);
                r.setVisibility(VISIBLE);
                mHandler.post(mTick);
            }
            setCanNext(!content.get(count).isPause());
            textView.setText(content.get(count).getText());
        }
    }
    private final Handler mHandler = new Handler();
    private int health=3;
    private int mana=3;
    private final Runnable mTick = new Runnable() {
        public void run() {
            if(content.get(count).isHealth())
            {
                if(health%2==1)
                    hi.setValue(hi.getValue()-20);
                health++;
                if(health>=6)
                {
                    health=0;
                    hi.setValue(hi.getValue()+60);
                }
            }
            if(content.get(count).isMana())
            {
                mi.setValue(mi.getValue()+5);
                mana++;
                if(mana>=3)
                {
                    mana=0;
                    mi.setValue(mi.getValue()-20);
                }
            }
            mHandler.postDelayed(this, 1000);
        }
    };
    void close() {
        mHandler.removeCallbacks(mTick);
        setEnabled(false);
        setVisibility(INVISIBLE);
        startAnimation(animFadeOut);
    }
    void setCanNext(boolean canNext) {
        nextButton.setEnabled(canNext);
        nextButton.setClickable(canNext);
    }
    public boolean isOnPause(){
        return !nextButton.isEnabled();
    }
}
