package forge.adventure.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TextraLabel;
import forge.Forge;
import forge.adventure.character.ShopActor;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.stage.GameHUD;
import forge.adventure.util.*;
import forge.adventure.world.WorldSave;
import forge.assets.ImageCache;
import forge.sound.SoundEffectType;
import forge.sound.SoundSystem;

/**
 * Displays the rewards of a fight or a treasure
 */
public class RewardScene extends UIScene {
    private TextraButton doneButton;
    private TextraLabel goldLabel;

    private static RewardScene object;

    public static RewardScene instance() {
        if(object==null)
            object=new RewardScene();
        return object;
    }

    private boolean showTooltips = false;
    public enum Type {
        Shop,
        Loot
    }

    Type type;
    Array<Actor> generated = new Array<>();
    static public final float CARD_WIDTH =550f ;
    static public final float CARD_HEIGHT = 400f;
    static public final float CARD_WIDTH_TO_HEIGHT = CARD_WIDTH / CARD_HEIGHT;

    private RewardScene() {

        super(Forge.isLandscapeMode() ? "ui/items.json" : "ui/items_portrait.json");

        goldLabel=ui.findActor("gold");
        ui.onButtonPress("done", () -> RewardScene.this.done());
        doneButton = ui.findActor("done");
    }

    boolean doneClicked = false, shown = false;
    float flipCountDown = 1.0f;
    float exitCountDown = 0.0f; //Serves as additional check for when scene is exiting, so you can't double tap too fast.

    public void quitScene() {
        //There were reports of memory leaks after using the shop many times, so remove() everything on exit to be sure.
        for(Actor A: new Array.ArrayIterator<>(generated)) {
            if(A instanceof RewardActor){
                ((RewardActor) A).removeTooltip();
                ((RewardActor) A).dispose();
                A.remove();
            }
        }
        //save RAM
        ImageCache.unloadCardTextures(true);
        Forge.switchToLast();
    }

    public boolean done() {
        GameHUD.getInstance().getTouchpad().setVisible(false);
        if (doneClicked) {
            if(exitCountDown > 0.2f) {
                clearGenerated();
                quitScene();
            }
            return true;
        }

        if (type == Type.Loot) {
            boolean wait = false;
            for (Actor actor : new Array.ArrayIterator<>(generated)) {
                if (!(actor instanceof RewardActor)) {
                    continue;
                }
                RewardActor reward = (RewardActor) actor;
                AdventurePlayer.current().addReward(reward.getReward());
                if (!reward.isFlipped()) {
                    wait = true;
                    reward.flip();
                }
            }
            if (wait) {
                flipCountDown = Math.min(1.0f + (generated.size * 0.3f), 5.0f);
                exitCountDown = 0.0f;
                doneClicked = true;
            } else {
                clearGenerated();
                quitScene();
            }
        } else {
            clearGenerated();
            quitScene();
        }
        return true;
    }
    void clearGenerated() {
        for (Actor actor : new Array.ArrayIterator<>(generated)) {
            if (!(actor instanceof RewardActor)) {
                continue;
            }
            RewardActor reward = (RewardActor) actor;
            reward.clearHoldToolTip();
            try {
                stage.getActors().removeValue(reward, true);
            } catch (Exception e) {}
        }
    }

    @Override
    public void act(float delta) {
        stage.act(delta);
        ImageCache.allowSingleLoad();
        if (doneClicked) {
            if (type == Type.Loot) {
                flipCountDown -= Gdx.graphics.getDeltaTime();
                exitCountDown += Gdx.graphics.getDeltaTime();
            }
            if (flipCountDown <= 0) {
                clearGenerated();
                quitScene();
            }
        }
    }


    @Override
    public boolean keyPressed(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
            done();
        }
        if (keycode == Input.Keys.BUTTON_B || keycode == Input.Keys.BUTTON_START)
            showLootOrDone();
        else if (keycode == Input.Keys.BUTTON_A)
            performTouch(selectedActor);
        else if (keycode == Input.Keys.DPAD_RIGHT) {
            hideTooltips();
            selectNextActor(false);
            if (selectedActor != null && Type.Loot == type) {
                selectedActor.fire(eventEnter);
            }
            showHideTooltips();
        } else if (keycode == Input.Keys.DPAD_LEFT) {
            hideTooltips();
            selectPreviousActor(false);
            if (selectedActor != null && Type.Loot == type) {
                selectedActor.fire(eventEnter);
            }
            showHideTooltips();
        } else if (keycode == Input.Keys.BUTTON_Y) {
            showTooltips = !showTooltips;
            showHideTooltips();
        }
        return true;
    }
    private void showHideTooltips() {
        if (selectedActor instanceof RewardActor) {
            if (showTooltips) {
                if (((RewardActor) selectedActor).isFlipped())
                    ((RewardActor) selectedActor).showTooltip();
            } else {
                ((RewardActor) selectedActor).hideTooltip();
            }
        } else if (selectedActor instanceof BuyButton) {
            if (showTooltips)
                ((BuyButton) selectedActor).reward.showTooltip();
            else
                ((BuyButton) selectedActor).reward.hideTooltip();
        }
    }
    private void hideTooltips() {
        if (selectedActor instanceof RewardActor) {
            ((RewardActor) selectedActor).hideTooltip();
        } else if (selectedActor instanceof BuyButton) {
            ((BuyButton) selectedActor).reward.hideTooltip();
        }
    }
    private void showLootOrDone() {
        boolean exit = true;
        for (Actor actor : new Array.ArrayIterator<>(generated)) {
            if (!(actor instanceof RewardActor)) {
                continue;
            }
            RewardActor reward = (RewardActor) actor;
            if (!reward.isFlipped()) {
                exit = false;
                break;
            }
        }
        if (exit)
            performTouch(doneButton);
        else if (type == Type.Loot && !shown) {
            shown = true;
            for (Actor actor : new Array.ArrayIterator<>(generated)) {
                if (!(actor instanceof RewardActor)) {
                    continue;
                }
                RewardActor reward = (RewardActor) actor;
                AdventurePlayer.current().addReward(reward.getReward());
                if (!reward.isFlipped()) {
                    Timer.schedule(new Timer.Task() {
                        @Override
                        public void run() {
                            reward.flip();
                        }
                    }, 0.09f);
                }
            }
        } else {
            performTouch(doneButton);
        }
    }


    public void loadRewards(Array<Reward> newRewards, Type type, ShopActor shopActor) {
        clearActorObjects();
        this.type   = type;
        doneClicked = false;
        for (Actor actor : new Array.ArrayIterator<>(generated)) {
            actor.remove();
            if (actor instanceof RewardActor) {
                ((RewardActor) actor).dispose();
            }
        }
        generated.clear();


        Actor card = ui.findActor("cards");
        if(type==Type.Shop) {
            goldLabel.setText("Gold:"+Current.player().getGold());
            Actor background = ui.findActor("market_background");
            if(background!=null)
                background.setVisible(true);
        } else {
            goldLabel.setText("");
            Actor background = ui.findActor("market_background");
            if(background!=null)
                background.setVisible(false);
        }
        // card.setDrawable(new TextureRegionDrawable(new Texture(Res.CurrentRes.GetFile("ui/transition.png"))));

        float targetWidth = card.getWidth();
        float targetHeight = card.getHeight();
        float xOff = card.getX();
        float yOff = card.getY();

        int numberOfRows = 0;
        float cardWidth = 0;
        float cardHeight = 0;
        float bestCardHeight = 0;
        int numberOfColumns = 0;
        float targetArea = targetHeight * targetWidth;
        float oldCardArea = 0;
        float newArea = 0;

        switch (type) {
            case Shop:
                doneButton.setText(Forge.getLocalizer().getMessage("lblLeave"));
                goldLabel.setText(Current.player().getGold()+"[+Gold]");
                break;
            case Loot:
                goldLabel.setText("");
                doneButton.setText(Forge.getLocalizer().getMessage("lblDone"));
                break;
        }
        for (int h = 1; h < targetHeight; h++) {
            cardHeight = h;
            if (type == Type.Shop) {
                cardHeight += doneButton.getHeight();
            }
            //cardHeight=targetHeight/i;
            cardWidth = h / CARD_WIDTH_TO_HEIGHT;
            newArea = newRewards.size * cardWidth * cardHeight;

            int rows = (int) (targetHeight / cardHeight);
            int cols = (int) Math.ceil(newRewards.size / (double) rows);
            if (newArea > oldCardArea && newArea <= targetArea && rows * cardHeight < targetHeight && cols * cardWidth < targetWidth) {
                oldCardArea = newArea;
                numberOfRows = rows;
                numberOfColumns = cols;
                bestCardHeight = h;
            }
        }
        float AR = 480f/270f;
        int x = Forge.getDeviceAdapter().getRealScreenSize(false).getLeft();
        int y = Forge.getDeviceAdapter().getRealScreenSize(false).getRight();
        int realX = Forge.getDeviceAdapter().getRealScreenSize(true).getLeft();
        int realY = Forge.getDeviceAdapter().getRealScreenSize(true).getRight();
        float fW = x > y ? x : y;
        float fH = x > y ? y : x;
        float mul = fW/fH < AR ? AR/(fW/fH) : (fW/fH)/AR;
        if (fW/fH >= 2f) {//tall display
            mul = (fW/fH) - ((fW/fH)/AR);
            if ((fW/fH) >= 2.1f && (fW/fH) < 2.2f)
                mul *= 0.9f;
            else if ((fW/fH) > 2.2f) //ultrawide 21:9 Galaxy Fold, Huawei X2, Xperia 1
                mul *= 0.8f;
        }
        cardHeight = bestCardHeight * 0.90f ;
        Float custom = Forge.isLandscapeMode() ? Config.instance().getSettingData().rewardCardAdjLandscape : Config.instance().getSettingData().rewardCardAdj;
        if (custom != null && custom != 1f) {
            mul *= custom;
        } else {
            if (realX > x || realY > y) {
                mul *= Forge.isLandscapeMode() ? 0.95f : 1.05f;
            } else {
                //immersive | no navigation and/or showing cutout cam
                if (fW/fH > 2.2f)
                    mul *= Forge.isLandscapeMode() ? 1.1f : 1.6f;
                else if (fW/fH >= 2.1f)
                    mul *= Forge.isLandscapeMode() ? 1.05f : 1.5f;
                else if (fW/fH >= 2f)
                    mul *= Forge.isLandscapeMode() ? 1f : 1.4f;

            }
        }
        cardWidth = (cardHeight / CARD_WIDTH_TO_HEIGHT)*mul;

        yOff += (targetHeight - (cardHeight * numberOfRows)) / 2f;
        xOff += (targetWidth - (cardWidth * numberOfColumns)) / 2f;

        float spacing = 2;
        int i = 0;
        for (Reward reward : new Array.ArrayIterator<>(newRewards)) {
            boolean skipCard = false;
            if (type == Type.Shop) {
                if (shopActor.getMapStage().getChanges().wasCardBought(shopActor.getObjectId(), i)) {
                    skipCard = true;
                }
            }


            int currentRow = (i / numberOfColumns);
            float lastRowXAdjust = 0;
            if (currentRow == numberOfRows - 1) {
                int lastRowCount = newRewards.size % numberOfColumns;
                if (lastRowCount != 0)
                    lastRowXAdjust = ((numberOfColumns * cardWidth) - (lastRowCount * cardWidth)) / 2;
            }
            RewardActor actor = new RewardActor(reward, type == Type.Loot);
            actor.setBounds(lastRowXAdjust + xOff + cardWidth * (i % numberOfColumns) + spacing, yOff + cardHeight * currentRow + spacing, cardWidth - spacing * 2, cardHeight - spacing * 2);

            if (type == Type.Shop) {
                if (currentRow != ((i + 1) / numberOfColumns))
                    yOff += doneButton.getHeight();

                TextraButton buyCardButton = new BuyButton(shopActor.getObjectId(), i, shopActor.isUnlimited()?null:shopActor.getMapStage().getChanges(), actor, doneButton);
                generated.add(buyCardButton);
                if (!skipCard) {
                    stage.addActor(buyCardButton);
                    addActorObject(buyCardButton);
                }
            } else {
                addActorObject(actor);
            }
            generated.add(actor);
            if (!skipCard) {
                stage.addActor(actor);
            }
            i++;
        }
        updateBuyButtons();
    }

    private void updateBuyButtons() {
        for (Actor actor : new Array.ArrayIterator<>(generated)) {
            if (actor instanceof BuyButton) {
                ((BuyButton) actor).update();
            }
        }
    }

    private class BuyButton extends TextraButton {
        private final int objectID;
        private final int index;
        private final PointOfInterestChanges changes;
        RewardActor reward;
        int price;

        void update() {
            setDisabled(WorldSave.getCurrentSave().getPlayer().getGold() < price);
        }

        public BuyButton(int id, int i, PointOfInterestChanges ch, RewardActor actor, TextraButton style) {
            super("", style.getStyle());
            this.objectID = id;
            this.index = i;
            this.changes = ch;
            reward = actor;
            setHeight(style.getHeight());
            setWidth(actor.getWidth());
            setX(actor.getX());
            setY(actor.getY() - getHeight());
            price = CardUtil.getRewardPrice(actor.getReward());
            price *= Current.player().goldModifier();
            setText("$ " + price);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                if (Current.player().getGold() >= price) {
                    if(changes!=null)
                        changes.buyCard(objectID, index);
                    Current.player().takeGold(price);
                    Current.player().addReward(reward.getReward());

                    Gdx.input.vibrate(5);
                    SoundSystem.instance.play(SoundEffectType.FlipCoin, false);

                    updateBuyButtons();
                    goldLabel.setText("Gold: " + String.valueOf(AdventurePlayer.current().getGold()));
                    if(changes==null)
                        return;
                    setDisabled(true);
                    reward.sold();
                    getColor().a = 0.5f;
                    setText("SOLD");
                    removeListener(this);
                }
                }
            });
        }
    }
}
