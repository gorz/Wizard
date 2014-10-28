package com.wizardfight.views;

import com.wizardfight.Buff;

/*
 * Class that contains links to player`s buff views
 */
public class BuffPanel {
	private final BuffPicture[] buffPics;
	public BuffPanel(BuffPicture[] buffPictures) {
		buffPics = buffPictures;
	}
	
	public void addBuff(Buff buff) {
        for (BuffPicture buffPic : buffPics) {
            Buff b = buffPic.getBuff();
            // if exists already
            if (b == buff) {
                return;
            }
            if (b == Buff.NONE) {
                buffPic.setBuff(buff);
                return;
            }
        }
	}
	
	public void removeBuff(Buff buff) {
		int rmIndex = -1;
		for(int i=0; i<buffPics.length; i++) {
			if(buffPics[i].getBuff() == buff) {
				rmIndex = i;
				break;
			}
		}
		if(rmIndex == -1) return;
		
		for(int i=rmIndex; i<buffPics.length-1; i++) {
			if(buffPics[i].getBuff() == Buff.NONE) break;
			
			Buff rightBuff = buffPics[i+1].getBuff();
			buffPics[i].setBuff(rightBuff);
		}
	}
	
	public void removeBuffs() {
        for (BuffPicture buffPic : buffPics) {
            buffPic.setBuff(Buff.NONE);
        }
	}
}
