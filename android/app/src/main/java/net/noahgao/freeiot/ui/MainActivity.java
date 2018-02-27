/*
 * Copyright (c) 2017. Noah Gao <noahgaocn@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.noahgao.freeiot.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import net.noahgao.freeiot.R;
import net.noahgao.freeiot.api.ApiClient;
import net.noahgao.freeiot.model.NotificationMetaModel;
import net.noahgao.freeiot.model.UserModel;
import net.noahgao.freeiot.ui.pages.IndexFragment;
import net.noahgao.freeiot.ui.pages.ProductsFragment;
import net.noahgao.freeiot.util.Auth;
import net.noahgao.freeiot.util.Badge;
import net.noahgao.freeiot.util.UpdateManager;

import java.util.Set;

import cn.jpush.android.api.JPushInterface;
import cn.jpush.android.api.TagAliasCallback;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends BaseActivity
        implements NavigationView.OnNavigationItemSelectedListener,
            IndexFragment.OnFragmentInteractionListener,
            ProductsFragment.OnFragmentInteractionListener {

    public UserModel mUser;
    FragmentManager fragmentManager = getSupportFragmentManager();
    private Fragment[] fragments={
            new IndexFragment(),
            new ProductsFragment()
    };
    private Fragment currentFragment;
    private int curPageIndex = -1;

    private int hot_number = 0;
    private TextView ui_hot = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(!Auth.check()) {
            Intent intentLogin = new Intent(MainActivity.this, LoginActivity.class);
            intentLogin.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);//LoginActivity不添加到后退栈
            startActivity(intentLogin);
        }
        JPushInterface.setAlias(MainActivity.this, Auth.getUser().get_id().replace("-", "@"), new TagAliasCallback() {
            @Override
            public void gotResult(int i, String s, Set<String> set) {
                Log.i("PUSH","Alias Set "+i+","+s);
            }
        });
        UpdateManager.doUpdate(this, false);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        if(mUser == null) mUser = Auth.getUser();
        TextView emailView = (TextView) ((NavigationView) findViewById(R.id.nav_view)).getHeaderView(0).findViewById(R.id.nav_header_email);
        TextView roleView = (TextView) ((NavigationView) findViewById(R.id.nav_view)).getHeaderView(0).findViewById(R.id.nav_header_role);
        emailView.setText(mUser.getEmail());
        roleView.setText(Badge.buildRole(mUser.getRole()));

        changePage(0);
    }

    @Override
    protected void onResume() {

        Call<NotificationMetaModel> modcall = ApiClient.API.getNotificationMeta(Auth.getToken());
        modcall.enqueue(new Callback<NotificationMetaModel>() {
            @Override
            public void onResponse(Call<NotificationMetaModel> call, Response<NotificationMetaModel> response) {
                updateHotCount(response.body().getUnread());
            }

            @Override
            public void onFailure(Call<NotificationMetaModel> call, Throwable t) {
                Toast.makeText(getApplicationContext(), "通知信息请求出错", Toast.LENGTH_SHORT).show();
            }
        });
        super.onResume();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            Log.i("MAIN","OnBackPressed");
            // super.onBackPressed(); 	// 不要调用父类的方法
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addCategory(Intent.CATEGORY_HOME);
            startActivity(intent);
        }
    }

    public void onFragmentInteraction() {}

    public void changePage(int tag) {
        if(curPageIndex != tag) {
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            if (currentFragment != null) fragmentTransaction.remove(currentFragment);
            fragmentTransaction.add(R.id.content_main, fragments[tag]);
            fragmentTransaction.commit();
            currentFragment = fragments[tag];
            NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
            navigationView.getMenu().getItem(0).setChecked(false);
            navigationView.getMenu().getItem(1).setChecked(false);
            switch (tag) {
                case 0:
                    navigationView.getMenu().getItem(0).setChecked(true);
                    break;
                case 1:
                    navigationView.getMenu().getItem(1).setChecked(true);
                    break;
            }
            curPageIndex = tag;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_toolbar,menu);
        final MenuItem t = menu.findItem(R.id.notification);
        final View menu_hotlist = t.getActionView();
        menu_hotlist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOptionsItemSelected(t);
            }
        });
        ui_hot = (TextView) menu_hotlist.findViewById(R.id.hotlist_hot);
        updateHotCount(hot_number);
        return super.onCreateOptionsMenu(menu);
    }

    public void updateHotCount(final int new_hot_number) {
        hot_number = new_hot_number;
        if (ui_hot == null) return;
        runOnUiThread(new Runnable() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                if (new_hot_number == 0)
                    ui_hot.setVisibility(View.INVISIBLE);
                else {
                    ui_hot.setVisibility(View.VISIBLE);
                    ui_hot.setText(Integer.toString(new_hot_number));
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.search:
                Toast.makeText(getApplicationContext(), "搜索功能尚未开放", Toast.LENGTH_SHORT).show();
                break;
            case R.id.notification:
                startActivity(new Intent(MainActivity.this,NotificationsActivity.class));
                /*
                Toast.makeText(getApplicationContext(), "暂时没有未读的通知", Toast.LENGTH_SHORT).show();
                updateHotCount(hot_number + 1);
                */
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            changePage(0);
        } else if (id == R.id.nav_products) {
            changePage(1);
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}