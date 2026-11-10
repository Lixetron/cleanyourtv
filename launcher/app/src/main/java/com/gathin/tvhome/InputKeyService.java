package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.Collections;
import java.util.Map;

/**
 * Maps the remote's Input button to the system input switcher, from any app. Remotes such as the Chromecast voice
 * remote send KEYCODE_TV for it, others (KTC and Kalley Realtek Google TV remotes) KEYCODE_TV_INPUT; on Google TV
 * the stock launcher handles both, so with the stock launcher disabled the button does nothing. Buttons listed in
 * input_key_keycodes (config.xml or a theme) open the input switcher the same way, and app_keys start an app (a
 * YouTube button that only the stock launcher answered). While Home is in front, the TV/Input button, and the buttons
 * in hold_input_keycodes (the Xiaomi MI button on the Mi TV 4S), get a second job: a tap opens the Live TV app as the
 * firmware's button does, and holding one opens Home's own Inputs menu. Every other key passes through untouched.
 * Opt-in: INPUT_KEY_SERVICE=1 build-and-install.sh (or -InputKeyService on Windows) enables it.
 */
public class InputKeyService extends AccessibilityService {

    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    private int[] inputKeycodes = new int[0];
    /** hold_input_keycodes: a tap opens Live TV on Home, a hold opens Home's Inputs menu. */
    private int[] holdInputKeycodes = new int[0];
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private Map<Integer, String> appScancodes = Collections.emptyMap();
    private Map<Integer, String> holdAppKeys = Collections.emptyMap();
    private Map<Integer, String> holdAppScancodes = Collections.emptyMap();
    /** app_keys "front:" entries, chosen the way HomeKeyService chooses them (RemoteKeys.tap). */
    private Map<Integer, String> frontAppKeys = Collections.emptyMap();
    private Map<Integer, String> frontAppScancodes = Collections.emptyMap();
    /** live_tv_package: where a tap on the TV/Input button goes while Home is in front. */
    private String liveTvPackage = "";
    /** The pending hold of the TV/Input button: after HOLD_MS it opens Home's own Inputs menu. */
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable holdAction;
    private boolean holdFired;
    /** restore_key_services was on when the config was read: this helper is noted for ServiceRepair. */
    private boolean repairRecord;
    /** consume_volume_keys: Volume Up/Down go to VolumeKeys instead of on to the firmware. */
    private boolean consumeVolumeKeys;
    /** Stops key filtering while a game controller is connected; null unless gamepad_passthrough and key_filter. */
    private GamepadPassthrough gamepads;
    /** The bound helper, for global actions Home's own key path asks for (RemoteKeys). */
    static InputKeyService running;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
        // From config.xml or a theme's config.json; theme.sh rebinds this service after a push.
        Config config = Config.load(this);
        // restore_key_services: note that this helper runs, so Home can put it back after a force stop.
        repairRecord = config.restoreKeyServices;
        if (repairRecord) ServiceRepair.connected(this, getClass());
        inputKeycodes = config.inputKeycodes;
        holdInputKeycodes = config.holdInputKeycodes;
        liveTvPackage = config.liveTvPackage;
        consumeVolumeKeys = config.consumeVolumeKeys;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys);
        RemoteKeys.applyKeyFilter(this, config.keyFilter);
        holdAppKeys = RemoteKeys.parseHoldAppKeys(config.appKeys);
        holdAppScancodes = RemoteKeys.parseHoldAppScancodes(config.appKeys);
        frontAppKeys = RemoteKeys.parseFrontAppKeys(config.appKeys);
        frontAppScancodes = RemoteKeys.parseFrontAppScancodes(config.appKeys);
        // gamepad_passthrough, off by default. Only with key_filter on: off, the service never filters keys.
        if (config.gamepadPassthrough && config.keyFilter && gamepads == null) {
            gamepads = new GamepadPassthrough(this);
            gamepads.start();
        }
    }

    private boolean isInputKey(int keyCode) {
        if (isTvKey(keyCode)) return true;
        for (int code : inputKeycodes) if (keyCode == code) return true;
        return false;
    }

    /** The remote's Live TV / Input button, whichever of the two keycodes it sends. */
    private static boolean isTvKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_TV || keyCode == KeyEvent.KEYCODE_TV_INPUT;
    }

    /** A button that gets Home's Inputs menu on a hold: the TV/Input keys and hold_input_keycodes. */
    private boolean isHomeInputKey(int keyCode) {
        if (isTvKey(keyCode)) return true;
        for (int code : holdInputKeycodes) if (keyCode == code) return true;
        return false;
    }

    /**
     * A button from hold_input_keycodes (or TV/Input) while Home is in front. Consumed on purpose: the firmware's own
     * receiver acts on the DOWN and would flash its source list up under the menu. A tap opens the Live TV app (the
     * firmware's own destination); holding it for HOLD_MS opens Home's Inputs menu instead.
     */
    private boolean inputKeyOnHome(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                holdFired = false;
                if (holdAction != null) handler.removeCallbacks(holdAction);
                holdAction = () -> {
                    holdFired = true;
                    RemoteKeys.launch(this, RemoteKeys.INPUTS);
                };
                handler.postDelayed(holdAction, RemoteKeys.HOLD_MS);
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (holdAction != null) handler.removeCallbacks(holdAction);
            holdAction = null;
            boolean tapped = !holdFired;
            holdFired = false;
            if (tapped) openLiveTv();
            return true;
        }
        return true;   // the repeat presses of a held button stay here too
    }

    /** The helper is leaving while the button may still be held: no menu, and the press opens nothing later. */
    private void releaseHold() {
        if (holdAction != null) handler.removeCallbacks(holdAction);
        holdAction = null;
        holdFired = true;   // an UP that still comes later must not open Live TV for a press this helper gave up
    }

    /** A tap on such a button: the Live TV app, or the system switcher on a set without one. */
    private void openLiveTv() {
        if (!liveTvPackage.isEmpty()) {
            RemoteKeys.launch(this, liveTvPackage);
            return;
        }
        try {
            startActivity(new Intent(ACTION_VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (consumeVolumeKeys && VolumeKeys.handle(this, event)) return true;
        // A scancode entry is more specific than any keycode, so it goes first.
        if (RemoteKeys.handle(this, event, RemoteKeys.tap(appScancodes.get(event.getScanCode()),
                frontAppScancodes.get(event.getScanCode())),
                holdAppScancodes.get(event.getScanCode()))) return true;
        if (RemoteKeys.handle(this, event, RemoteKeys.tap(appKeys.get(event.getKeyCode()),
                frontAppKeys.get(event.getKeyCode())),
                holdAppKeys.get(event.getKeyCode()))) return true;
        if (isHomeInputKey(event.getKeyCode())) {
            // On Home the remote's Live TV/Input button becomes the launcher's: a tap opens Live TV, a hold opens
            // Home's own Inputs menu. Elsewhere the firmware keeps its button, exactly as without this helper.
            if (MainActivity.inFront()) return inputKeyOnHome(event);
            return false;
        }
        if (!isInputKey(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            try {
                startActivity(new Intent(ACTION_VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
            }
        }
        return true;
    }

    /** Unbound by Android: the user turned the helper off, so Home doesn't put it back (ServiceRepair). */
    @Override
    public boolean onUnbind(Intent intent) {
        RemoteKeys.release(this);
        releaseHold();
        if (repairRecord) ServiceRepair.disconnected(this, getClass());
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        RemoteKeys.release(this);
        releaseHold();
        if (gamepads != null) gamepads.stop();
        if (consumeVolumeKeys) VolumeKeys.stop();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
