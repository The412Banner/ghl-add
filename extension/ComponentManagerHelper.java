package app.revanced.extension.gamehub;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.util.List;

/**
 * Static helpers called from smali code injected into GameHub Lite 5.1.4.
 *
 * <p>All methods are called from GameHub's own bytecode, so they use reflection
 * for any GameHub-internal classes.</p>
 *
 * <p>MenuItem constructor in 5.1.4:
 * {@code <init>(int id, int iconRes, String name, String rightContent, int mask,
 * DefaultConstructorMarker)}</p>
 */
@SuppressWarnings("unused")
public final class ComponentManagerHelper {

    private static final String TAG = "BannerHub";
    private static final int COMPONENTS_MENU_ID = 9;

    private ComponentManagerHelper() {}

    // ── Called from HomeLeftMenuDialog.Z0() ──────────────────────────────────

    /**
     * Appends a "Components" menu item to the sidebar item list.
     *
     * @param items the live List&lt;HomeLeftMenuDialog.MenuItem&gt; (this.m from Z0)
     */
    @SuppressWarnings("rawtypes")
    public static void addComponentsMenuItem(List items) {
        try {
            android.content.Context ctx = (android.content.Context)
                    Class.forName("android.app.ActivityThread")
                            .getMethod("currentApplication")
                            .invoke(null);

            Class<?> menuItemClass = Class.forName(
                    "com.xj.landscape.launcher.ui.menu.HomeLeftMenuDialog$MenuItem");
            Class<?> markerClass = Class.forName(
                    "kotlin.jvm.internal.DefaultConstructorMarker");

            // 5.1.4 constructor: <init>(int id, int iconRes, String name,
            //                           String rightContent, int mask, DefaultConstructorMarker)
            Constructor<?> ctor = menuItemClass.getDeclaredConstructor(
                    int.class, int.class, String.class, String.class,
                    int.class, markerClass);
            ctor.setAccessible(true);

            int iconRes = ctx.getResources().getIdentifier(
                    "menu_setting_normal", "drawable", ctx.getPackageName());

            // mask=0x8: bit3 set → rightContent uses Kotlin default (null)
            Object item = ctor.newInstance(
                    COMPONENTS_MENU_ID, iconRes, "Components", null, 0x8, null);

            //noinspection unchecked
            items.add(item);
        } catch (Exception e) {
            Log.e(TAG, "addComponentsMenuItem failed", e);
        }
    }

    // ── Called from HomeLeftMenuDialog$init$1$9$2.a(MenuItem) ────────────────

    /**
     * Intercepts the sidebar item click before the packed-switch runs.
     *
     * @param dialog    the HomeLeftMenuDialog fragment (this.a from the lambda)
     * @param menuItem  the HomeLeftMenuDialog.MenuItem that was clicked (p1)
     * @param activity  the hosting FragmentActivity (this.b from the lambda)
     * @return true if we handled the click (ID=9), false to let the original switch run
     */
    public static boolean handleMenuItemClick(Object dialog, Object menuItem, Activity activity) {
        try {
            int id = (int) menuItem.getClass().getMethod("a").invoke(menuItem);
            if (id != COMPONENTS_MENU_ID) return false;

            // Dismiss the dialog first (same as the original packed-switch cases do)
            dialog.getClass().getMethod("dismiss").invoke(dialog);

            Intent intent = new Intent(activity, ComponentManagerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "handleMenuItemClick failed", e);
            return false;
        }
    }

}
