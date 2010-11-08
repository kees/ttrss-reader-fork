/*
 * ttrss-reader-fork for Android
 * 
 * Copyright (C) 2010 N. Braden.
 * Copyright (C) 2009-2010 J. Devauchelle.
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

package org.ttrssreader.gui.activities;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.ttrssreader.R;
import org.ttrssreader.controllers.Controller;
import org.ttrssreader.controllers.DBHelper;
import org.ttrssreader.controllers.Data;
import org.ttrssreader.gui.IRefreshEndListener;
import org.ttrssreader.gui.IUpdateEndListener;
import org.ttrssreader.model.ReadStateUpdater;
import org.ttrssreader.model.Refresher;
import org.ttrssreader.model.Updater;
import org.ttrssreader.model.article.ArticleItem;
import org.ttrssreader.model.feedheadline.FeedHeadlineListAdapter;
import org.ttrssreader.utils.Utils;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.os.AsyncTask.Status;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.GestureDetector.OnGestureListener;
import android.widget.ListView;

public class FeedHeadlineListActivity extends ListActivity implements IRefreshEndListener, IUpdateEndListener {
    
    private static final int MENU_REFRESH = Menu.FIRST;
    private static final int MENU_MARK_ALL_READ = Menu.FIRST + 1;
    private static final int MENU_MARK_ALL_UNREAD = Menu.FIRST + 2;
    private static final int MENU_DISPLAY_ONLY_UNREAD = Menu.FIRST + 3;
    
    public static final String FEED_ID = "FEED_ID";
    public static final String FEED_TITLE = "FEED_TITLE";
    public static final String FEED_LIST = "FEED_LIST";
    public static final String FEED_LIST_ID = "FEED_LIST_ID";
    public static final String FEED_LIST_NAME = "FEED_LIST_NAME";
    
    public boolean flingDetected = false;
    
    private int mFeedId;
    private String mFeedTitle;
    private ArrayList<Integer> mFeedListIds;
    private ArrayList<String> mFeedListNames;
    private GestureDetector mGestureDetector;
    
    private ListView mFeedHeadlineListView;
    private FeedHeadlineListAdapter mAdapter = null;
    private Refresher refresher;
    private Updater updater;
    
    @Override
    protected void onCreate(Bundle instance) {
        super.onCreate(instance);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.feedheadlinelist);
        
        Controller.getInstance().checkAndInitializeController(this);
        DBHelper.getInstance().checkAndInitializeDB(this);
        Data.getInstance().checkAndInitializeData(this);
        
        mFeedHeadlineListView = getListView();
        mGestureDetector = new GestureDetector(onGestureListener);
        
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mFeedId = extras.getInt(FEED_ID);
            mFeedTitle = extras.getString(FEED_TITLE);
            mFeedListIds = extras.getIntegerArrayList(FEED_LIST_ID);
            mFeedListNames = extras.getStringArrayList(FEED_LIST_NAME);
        } else if (instance != null) {
            mFeedId = instance.getInt(FEED_ID);
            mFeedTitle = instance.getString(FEED_TITLE);
            mFeedListIds = instance.getIntegerArrayList(FEED_LIST_ID);
            mFeedListNames = instance.getStringArrayList(FEED_LIST_NAME);
        } else {
            mFeedId = -1;
            mFeedTitle = null;
            mFeedListIds = null;
            mFeedListNames = null;
        }
        
        mAdapter = new FeedHeadlineListAdapter(this, mFeedId);
        mFeedHeadlineListView.setAdapter(mAdapter);
        doUpdate();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        DBHelper.getInstance().checkAndInitializeDB(getApplicationContext());
        doRefresh();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refresher != null) {
            refresher.cancel(true);
            refresher = null;
        }
        if (updater != null) {
            updater.cancel(true);
            updater = null;
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(FEED_ID, mFeedId);
        outState.putString(FEED_TITLE, mFeedTitle);
        outState.putIntegerArrayList(FEED_LIST_ID, mFeedListIds);
        outState.putStringArrayList(FEED_LIST_NAME, mFeedListNames);
    }
    
    private void doRefresh() {
        // reset fling-status
        flingDetected = false;
        
        // Only update if no refresher already running
        if (refresher != null) {
            if (refresher.getStatus().equals(AsyncTask.Status.PENDING)) {
                return;
            } else if (refresher.getStatus().equals(AsyncTask.Status.FINISHED)) {
                refresher = null;
                return;
            }
        }
        
        if (mAdapter == null) {
            mAdapter = new FeedHeadlineListAdapter(this, mFeedId);
            mFeedHeadlineListView.setAdapter(mAdapter);
        }

        setProgressBarIndeterminateVisibility(true);
        refresher = new Refresher(this, mAdapter);
        refresher.execute();
    }
    
    private synchronized void doUpdate() {
        // Only update if no updater already running
        if (updater != null) {
            if (updater.getStatus().equals(AsyncTask.Status.PENDING)) {
                return;
            } else if (updater.getStatus().equals(AsyncTask.Status.FINISHED)) {
                updater = null;
                return;
            }
        }
        
        if (mAdapter == null) {
            mAdapter = new FeedHeadlineListAdapter(this, mFeedId);
            mFeedHeadlineListView.setAdapter(mAdapter);
        }
        
        updater = new Updater(this, mAdapter);
        new Handler().postDelayed(new Runnable() {
            public void run() {
                if (updater != null) {
                    setProgressBarIndeterminateVisibility(true);
                    updater.execute();
                }
            }
        }, Utils.WAIT);
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        
        Intent i = new Intent(this, ArticleActivity.class);
        i.putExtra(ArticleActivity.ARTICLE_ID, mAdapter.getFeedItemId(position));
        i.putExtra(ArticleActivity.FEED_ID, mFeedId);
        i.putIntegerArrayListExtra(ArticleActivity.ARTICLE_LIST_ID, mAdapter.getFeedItemIds());
        
        if (!flingDetected) {
            startActivity(i);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        MenuItem item;
        item = menu.add(0, MENU_REFRESH, 0, R.string.Main_RefreshMenu);
        item.setIcon(R.drawable.refresh32);
        item = menu.add(0, MENU_MARK_ALL_READ, 0, R.string.FeedHeadlinesListActivity_MarkAllRead);
        item = menu.add(0, MENU_MARK_ALL_UNREAD, 0, R.string.FeedHeadlinesListActivity_MarkAllUnread);
        item = menu.add(0, MENU_DISPLAY_ONLY_UNREAD, 0, R.string.Commons_DisplayOnlyUnread);
        return true;
    }
    
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case MENU_REFRESH:
                Data.getInstance().resetArticlesTime(mFeedId);
                doUpdate();
                doRefresh();
                return true;
            case MENU_MARK_ALL_READ:
                new Updater(this, new ReadStateUpdater(mAdapter.getArticles(), mFeedId, 0)).execute();
                return true;
            case MENU_MARK_ALL_UNREAD:
                new Updater(this, new ReadStateUpdater(mAdapter.getArticles(), mFeedId, 1)).execute();
                return true;
            case MENU_DISPLAY_ONLY_UNREAD:
                boolean displayOnlyUnread = Controller.getInstance().isDisplayOnlyUnread();
                Controller.getInstance().setDisplayOnlyUnread(!displayOnlyUnread);
                doRefresh();
                return true;
        }
        
        return super.onMenuItemSelected(featureId, item);
    }

    private void openNextFeed(int direction) {
        if (mFeedId < 0)
            return;
        
        int index = mFeedListIds.indexOf(mFeedId) + direction;
        
        // No more feeds in this direction
        if (index < 0 || index >= mFeedListIds.size()) {
            if (Controller.getInstance().isVibrateOnLastArticle()) {
                Log.i(Utils.TAG, "No more feeds, vibrate..");
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(Utils.SHORT_VIBRATE);
            }
            return;
        }
        
        Intent i = new Intent(this, this.getClass());
        i.putExtra(FEED_ID, mFeedListIds.get(index));
        i.putExtra(FEED_TITLE, mFeedListNames.get(index));
        i.putIntegerArrayListExtra(FEED_LIST_ID, mFeedListIds);
        i.putStringArrayListExtra(FEED_LIST_NAME, mFeedListNames);
        
        startActivityForResult(i, 0);
        this.finish();
    }
    
    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        super.dispatchTouchEvent(e);
        return mGestureDetector.onTouchEvent(e);
    }
    
    private OnGestureListener onGestureListener = new OnGestureListener() {
        
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            
            int dx = (int) (e2.getX() - e1.getX());
            int dy = (int) (e2.getY() - e1.getY());
            
            if (Math.abs(dy) > 60) {
                // Too much Y-Movement
                return false;
            } else if (!Controller.getInstance().isUseSwipe()) {
                return false;
            }
            
            // don't accept the fling if it's too short as it may conflict with a button push
            if (Math.abs(dx) > 80 && Math.abs(velocityX) > Math.abs(velocityY)) {
                flingDetected = true;
                
//                Log.d(Utils.TAG, "Fling: (" + e1.getX() + " " + e1.getY() + ")(" + e2.getX() + " " + e2.getY()
//                        + ") dx: " + dx + " dy: " + dy + " (Direction: " + ((velocityX > 0) ? "right" : "left"));
                
                if (velocityX > 0) {
                    openNextFeed(-1);
                } else {
                    openNextFeed(1);
                }
                return true;
            }
            
            return false;
        }
        
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
//            if (distanceX > distanceY)
//                return true;
            return false;
        }
        
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return false;
        }
        
        @Override
        public boolean onDown(MotionEvent e) {
            return false;
        }
        
        @Override
        public void onLongPress(MotionEvent e) {
        }
        
        @Override
        public void onShowPress(MotionEvent e) {
        }
    };
    
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Controller.getInstance().isUseVolumeKeys()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                openNextFeed(-1);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                openNextFeed(1);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (Controller.getInstance().isUseVolumeKeys()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }
    
    private void openConnectionErrorDialog(String errorMessage) {
        if (refresher != null) {
            refresher.cancel(true);
            refresher = null;
        }
        if (updater != null) {
            updater.cancel(true);
            updater = null;
        }
        
        Intent i = new Intent(this, ErrorActivity.class);
        i.putExtra(ErrorActivity.ERROR_MESSAGE, errorMessage);
        startActivityForResult(i, ErrorActivity.ACTIVITY_SHOW_ERROR);
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ErrorActivity.ACTIVITY_SHOW_ERROR) {
            doUpdate();
            doRefresh();
        }
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public void onRefreshEnd() {
        if (!Controller.getInstance().getConnector().hasLastError()) {
            
            try {
                List<ArticleItem> list = new ArrayList<ArticleItem>();
                list.addAll((Set<ArticleItem>) refresher.get());
                refresher= null;
                mAdapter.setArticles(list);
                mAdapter.notifyDataSetChanged();
            } catch (Exception e) {
            }
            
            if (mFeedTitle != null) {
                this.setTitle(mFeedTitle + " (" + mAdapter.getUnreadCount() + ")");
            } else {
                this.setTitle(this.getResources().getString(R.string.ApplicationName) + " ("
                        + mAdapter.getUnreadCount() + ")");
            }
        } else {
            openConnectionErrorDialog(Controller.getInstance().getConnector().pullLastError());
        }
        
        if (updater != null) {
            if (updater.getStatus().equals(Status.FINISHED)) {
                setProgressBarIndeterminateVisibility(false);
            }
        } else {
            setProgressBarIndeterminateVisibility(false);
        }
    }
    
    @Override
    public void onUpdateEnd() {
        updater = null;
        doRefresh();
    }
    
}
