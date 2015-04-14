package com.m.ui.activity.basic;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import com.m.R;
import com.m.common.setting.SettingUtility;
import com.m.common.utils.CommSettings;
import com.m.common.utils.Logger;
import com.m.common.utils.ViewUtils;
import com.m.component.bitmaploader.BitmapLoader;
import com.m.component.bitmaploader.core.BitmapOwner;
import com.m.network.task.ITaskManager;
import com.m.network.task.TaskManager;
import com.m.network.task.WorkTask;
import com.m.support.inject.InjectUtility;
import com.m.ui.fragment.ABaseFragment;

/**
 * Created by wangdan on 15-1-16.
 */
public class BaseActivity extends ActionBarActivity implements BitmapOwner, ITaskManager {

    static final String TAG = "Activity-Base";

    private int theme = 0;// 当前界面设置的主题

    private Locale language = null;// 当前界面的语言

    private TaskManager taskManager;

    private boolean isDestory;

    // 当有Fragment Attach到这个Activity的时候，就会保存
    private Map<String, WeakReference<ABaseFragment>> fragmentRefs;

    private static BaseActivity runningActivity;

    private View rootView;

    private Toolbar mToolbar;

    public static BaseActivity getRunningActivity() {
        return runningActivity;
    }

    public static void setRunningActivity(BaseActivity activity) {
        runningActivity = activity;
    }

    protected int configTheme() {
        return CommSettings.getAppTheme();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        fragmentRefs = new HashMap<String, WeakReference<ABaseFragment>>();

        if (savedInstanceState == null) {
            theme = configTheme();

            language = new Locale(SettingUtility.getPermanentSettingAsStr("language", Locale.getDefault().getLanguage()),
                    SettingUtility.getPermanentSettingAsStr("language-country", Locale.getDefault().getCountry()));
        } else {
            theme = savedInstanceState.getInt("theme");

            language = new Locale(savedInstanceState.getString("language"), savedInstanceState.getString("language-country"));
        }
        // 设置主题
        if (theme > 0)
            setTheme(theme);
        // 设置语言
        setLanguage(language);

        taskManager = new TaskManager();

        // 如果设备有实体MENU按键，overflow菜单不会再显示
        ViewConfiguration viewConfiguration = ViewConfiguration.get(this);
        if (viewConfiguration.hasPermanentMenuKey()) {
            try {
                Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(viewConfiguration, false);
            } catch (Exception e) {
            }
        }

        super.onCreate(savedInstanceState);
    }

    public Toolbar getToolbar() {
        return mToolbar;
    }

    @Override
    public void setContentView(int layoutResID) {
        setContentView(View.inflate(this, layoutResID, null));
    }

    View getRootView() {
        return rootView;
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);

        rootView = view;

        InjectUtility.initInjectedView(this);
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rootView = view;

        InjectUtility.initInjectedView(this);
        
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        if (mToolbar != null)
            setSupportActionBar(mToolbar);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt("theme", theme);
        outState.putString("language", language.getLanguage());
        outState.putString("language-country", language.getCountry());
    }

    public void addFragment(String tag, ABaseFragment fragment) {
        fragmentRefs.put(tag, new WeakReference<ABaseFragment>(fragment));
    }

    public void removeFragment(String tag) {
        fragmentRefs.remove(tag);
    }

    @Override
    protected void onResume() {
        super.onResume();

        runningActivity = this;

        if (theme == configTheme()) {

        } else {
            Logger.i("theme changed, reload()");
            reload();

            return;
        }

        String languageStr = SettingUtility.getPermanentSettingAsStr("language", Locale.getDefault().getLanguage());
        String country = SettingUtility.getPermanentSettingAsStr("language-country", Locale.getDefault().getCountry());
        if (language != null && language.getLanguage().equals(languageStr) && country.equals(language.getCountry())) {

        }
        else {
            Logger.i("language changed, reload()");
            reload();

            return;
        }
    }

    public void setLanguage(Locale locale) {
        Resources resources = getResources();//获得res资源对象
        Configuration config = resources.getConfiguration();//获得设置对象
        config.locale = locale;
        DisplayMetrics dm = resources.getDisplayMetrics();//获得屏幕参数：主要是分辨率，像素等。
        resources.updateConfiguration(config, dm);
    }

    public void reload() {
        Intent intent = getIntent();
        overridePendingTransition(0, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
//		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        finish();

        overridePendingTransition(0, 0);
        startActivity(intent);
    }

    @Override
    public void onDestroy() {

        isDestory = true;

        removeAllTask(true);

        if (BitmapLoader.getInstance() != null)
        	BitmapLoader.getInstance().cancelPotentialTask(this);

        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (onHomeClick())
                    return true;
                break;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    protected boolean onHomeClick() {
        return onBackClick();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (onBackClick())
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public boolean onBackClick() {
        Set<String> keys = fragmentRefs.keySet();
        for (String key : keys) {
            WeakReference<ABaseFragment> fragmentRef = fragmentRefs.get(key);
            ABaseFragment fragment = fragmentRef.get();
            if (fragment != null && fragment.onBackClick())
                return true;
        }

        finish();

        return true;
    }

    @Override
    final public void addTask(@SuppressWarnings("rawtypes") WorkTask task) {
        taskManager.addTask(task);
    }

    @Override
    final public void removeTask(String taskId, boolean cancelIfRunning) {
        taskManager.removeTask(taskId, cancelIfRunning);
    }

    @Override
    final public void removeAllTask(boolean cancelIfRunning) {
        taskManager.removeAllTask(cancelIfRunning);
    }

    @Override
    final public int getTaskCount(String taskId) {
        return taskManager.getTaskCount(taskId);
    }

    /**
     * 以Toast形式显示一个消息
     *
     * @param msg
     */
    public void showMessage(CharSequence msg) {
        ViewUtils.showMessage(msg.toString());
    }

    /**
     * @param msgId
     */
    public void showMessage(int msgId) {
        showMessage(getText(msgId));
    }

    @Override
    public void finish() {
        // 2014-09-12 解决ATabTitlePagerFragment的destoryFragments方法报错的BUG
        setMDestory(true);

        super.finish();
    }

    public boolean mIsDestoryed() {
        return isDestory;
    }

    public void setMDestory(boolean destory) {
        this.isDestory = destory;
    }

    @Override
    public boolean canDisplay() {
        return true;
    }

}
