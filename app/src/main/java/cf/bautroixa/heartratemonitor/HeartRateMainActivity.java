package cf.bautroixa.heartratemonitor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import cf.bautroixa.heartratemonitor.data.AppSharedPreferences;
import cf.bautroixa.heartratemonitor.data.DBConstant;
import cf.bautroixa.heartratemonitor.home.HomeFragment;
import cf.bautroixa.heartratemonitor.home.TrendFragment;
import cf.bautroixa.heartratemonitor.theme.GotItDialog;
import cf.bautroixa.heartratemonitor.theme.MTTPCustom;
import cf.bautroixa.heartratemonitor.data.HeartRateConstant;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;

/**
 * đo nhịp tim #1.3
 */
public class HeartRateMainActivity extends AppCompatActivity implements HeartRateConstant, DBConstant {

    private ViewPager viewPager;
    private TabLayout tabLayout;
    private TextView txtLargeTitle, txtSmallTitle;
    private Toolbar toolbar;
    private MotionLayout motionLayout;
    private static FloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            setTheme(R.style.NoActionBar);
            setContentView(R.layout.activity_heart_rate_main_appbar);
            txtLargeTitle = findViewById(R.id.txtLargeTitle);
            txtSmallTitle = findViewById(R.id.txtSmallTitle);
            toolbar = findViewById(R.id.scrollable_toolbar);
            motionLayout = findViewById(R.id.appbar_container);
            motionLayout.setProgress(100f);
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        } else {
            setContentView(R.layout.activity_heart_rate_main);
            getSupportActionBar().setElevation(0);
        }
        setTitle(getString(R.string.heart_rate_monitor));

        viewPager = findViewById(R.id.view_pager);
        tabLayout = findViewById(R.id.tab_layout);
        fab = findViewById(R.id.fab_measure_hr);
        fab.hide();
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                viewPager.setCurrentItem(0);
                Intent intent = new Intent(v.getContext(), MeasuringActivity.class);
                startActivity(intent);
            }
        });

        viewPager.setAdapter(new TabViewPager(getSupportFragmentManager()));
        tabLayout.setupWithViewPager(viewPager);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 1: // trend tab
                        fab.show();
                        break;
                    default:
                        // home tab
                        fab.hide();
                        motionLayout.setProgress(100f);
                        break;
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT && txtLargeTitle!=null){
            txtLargeTitle.setText(title);
            txtSmallTitle.setText(title);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_heart_rate_main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void showFabTutorial() {
        new MaterialTapTargetPrompt.Builder(HeartRateMainActivity.this)
                .setTarget(fab)
                .setPrimaryText(R.string.hr_alter_measure_btn_tutorial)
                .setPromptBackground(new MTTPCustom.DimmedCirclePromptBackground())
                .show();
        AppSharedPreferences.getInstance(this).getEditor().putBoolean(KEY_TUTORIAL_TREND_FRAG, false).commit();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        int activeTabNumber = intent.getIntExtra(EXTRA_TAB_NUMBER, 0);
        Log.d(getLocalClassName(), "active tab= " + activeTabNumber);
        viewPager.setCurrentItem(activeTabNumber);
        viewPager.getAdapter().notifyDataSetChanged();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // back btn
                return true;
            case R.id.menu_info:
                GotItDialog aboutDialog = new GotItDialog(R.string.about, R.string.about_desc, new Runnable() {
                    @Override
                    public void run() {
                        // do nothing :V
                    }
                });
                aboutDialog.show(getSupportFragmentManager(),"about_dialog");
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public class TabViewPager extends FragmentPagerAdapter {

        public TabViewPager(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new HomeFragment();
                case 1:
                    TrendFragment trendFragment = new TrendFragment();
                    trendFragment.setOnTutorialFinishedListener(new TrendFragment.OnTutorialFinished() {
                        @Override
                        public void onFinished() {
                            showFabTutorial();
                        }
                    });
                    return trendFragment;
                default:
                    return new HomeFragment();
            }
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.heart_rate_monitor);
                case 1:
                    return getString(R.string.trending);
                default:
                    return getString(R.string.trending);
            }
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            return POSITION_NONE;
//            return super.getItemPosition(object);
        }
    }
}
