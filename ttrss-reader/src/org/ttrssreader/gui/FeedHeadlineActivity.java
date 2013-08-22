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

package org.ttrssreader.gui;

import org.ttrssreader.R;
import org.ttrssreader.controllers.Controller;
import org.ttrssreader.controllers.Data;
import org.ttrssreader.gui.dialogs.FeedUnsubscribeDialog;
import org.ttrssreader.gui.fragments.ArticleFragment;
import org.ttrssreader.gui.fragments.FeedHeadlineListFragment;
import org.ttrssreader.model.FeedAdapter;
import org.ttrssreader.model.updaters.ReadStateUpdater;
import org.ttrssreader.model.updaters.Updater;
import org.ttrssreader.utils.AsyncTask;
import org.ttrssreader.utils.Utils;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import com.actionbarsherlock.view.MenuItem;

public class FeedHeadlineActivity extends MenuActivity {
    
    public static final int FEED_NO_ID = 37846914;
    private static final String FRAGMENT = "HEADLINE_FRAGMENT";
    
    private int categoryId = -1000;
    private int feedId = -1000;
    private boolean selectArticlesForCategory = false;
    
    private GestureDetector gestureDetector;
    
    private FeedHeadlineUpdater headlineUpdater = null;
    private int[] parentIDs = new int[2];
    private String title = "";
    private int unreadCount = 0;
    
    @Override
    protected void onCreate(Bundle instance) {
        super.onCreate(instance);
        setContentView(R.layout.feedheadlinelist);
        
        gestureDetector = new GestureDetector(this, new MyGestureDetector());
        
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            categoryId = extras.getInt(FeedHeadlineListFragment.FEED_CAT_ID);
            feedId = extras.getInt(FeedHeadlineListFragment.FEED_ID);
            selectArticlesForCategory = extras.getBoolean(FeedHeadlineListFragment.FEED_SELECT_ARTICLES);
        } else if (instance != null) {
            categoryId = instance.getInt(FeedHeadlineListFragment.FEED_CAT_ID);
            feedId = instance.getInt(FeedHeadlineListFragment.FEED_ID);
            selectArticlesForCategory = instance.getBoolean(FeedHeadlineListFragment.FEED_SELECT_ARTICLES);
        }
        
        if (getSupportFragmentManager().findFragmentByTag(FRAGMENT) == null) {
            Fragment fragment = FeedHeadlineListFragment.newInstance(feedId, categoryId, selectArticlesForCategory);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.headline_list, fragment, FRAGMENT);
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            transaction.commit();
        }
        
        initialize();
    }
    
    private void initialize() {
        Controller.getInstance().lastOpenedFeeds.add(feedId);
        Controller.getInstance().lastOpenedArticles.clear();
        fillParentInformation();
    }
    
    private void fillParentInformation() {
        FeedAdapter parentAdapter = null;
        try {
            parentAdapter = new FeedAdapter(getApplicationContext(), categoryId);
            int index = parentAdapter.getIds().indexOf(feedId);
            if (index >= 0) {
                parentIDs[0] = parentAdapter.getId(index - 1); // Previous
                parentIDs[1] = parentAdapter.getId(index + 1); // Next
                
                if (parentIDs[0] == 0)
                    parentIDs[0] = -1;
                if (parentIDs[1] == 0)
                    parentIDs[1] = -1;
            }
        } finally {
            if (parentAdapter != null)
                parentAdapter.close();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        refreshAndUpdate();
    }
    
    @Override
    protected void doRefresh() {
        super.doRefresh();
        setTitle(title);
        setUnread(unreadCount);
        Utils.doRefreshFragment(getSupportFragmentManager().findFragmentById(R.id.headline_list));
    }
    
    @Override
    protected void doUpdate(boolean forceUpdate) {
        // Only update if no headlineUpdater already running
        if (headlineUpdater != null) {
            if (headlineUpdater.getStatus().equals(AsyncTask.Status.FINISHED)) {
                headlineUpdater = null;
            } else {
                return;
            }
        }
        
        if (!isCacherRunning()) {
            headlineUpdater = new FeedHeadlineUpdater(forceUpdate);
            headlineUpdater.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }
    
    @Override
    public final boolean onOptionsItemSelected(final MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case R.id.Menu_Refresh:
                doUpdate(true);
                return true;
            case R.id.Menu_MarkAllRead:
                if (selectArticlesForCategory) {
                    new Updater(this, new ReadStateUpdater(categoryId)).exec();
                } else {
                    new Updater(this, new ReadStateUpdater(feedId, 42)).exec();
                }
                
                if (Controller.getInstance().goBackAfterMakeAllRead())
                    onBackPressed();
                
                return true;
            case R.id.Menu_FeedUnsubscribe:
                FeedUnsubscribeDialog.getInstance(this, feedId).show(getSupportFragmentManager(),
                        FeedUnsubscribeDialog.DIALOG_UNSUBSCRIBE);
            default:
                return false;
        }
    }
    
    private void openNextFeed(int direction) {
        if (feedId < 0)
            return;
        
        int id = direction < 0 ? parentIDs[0] : parentIDs[1];
        if (id <= 0) {
            ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(Utils.SHORT_VIBRATE);
            return;
        }
        
        this.feedId = id;
        
        FeedHeadlineListFragment feedHeadlineView = FeedHeadlineListFragment.newInstance(feedId, categoryId,
                selectArticlesForCategory);
        
        // Replace the old fragment with the new one
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.headline_list, feedHeadlineView);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();
        
        initialize();
        doRefresh();
    }
    
    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (!gestureDetector.onTouchEvent(e))
            return super.dispatchTouchEvent(e);
        return true;
    }
    
    class MyGestureDetector extends SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // Refresh metrics-data in Controller
            Controller.refreshDisplayMetrics(((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay());
            
            try {
                if (Math.abs(e1.getY() - e2.getY()) > Controller.relSwipeMaxOffPath)
                    return false;
                if (e1.getX() - e2.getX() > Controller.relSwipeMinDistance
                        && Math.abs(velocityX) > Controller.relSwipteThresholdVelocity) {
                    
                    // right to left swipe
                    openNextFeed(1);
                    
                } else if (e2.getX() - e1.getX() > Controller.relSwipeMinDistance
                        && Math.abs(velocityX) > Controller.relSwipteThresholdVelocity) {
                    
                    // left to right swipe
                    openNextFeed(-1);
                    
                }
            } catch (Exception e) {
            }
            return false;
        }
    };
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Controller.getInstance().useVolumeKeys()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_N) {
                openNextFeed(-1);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_B) {
                openNextFeed(1);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (Controller.getInstance().useVolumeKeys()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                    || keyCode == KeyEvent.KEYCODE_N || keyCode == KeyEvent.KEYCODE_B) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }
    
    /**
     * Updates all articles from the selected feed.
     */
    public class FeedHeadlineUpdater extends ActivityUpdater {
        private static final int DEFAULT_TASK_COUNT = 2;
        
        public FeedHeadlineUpdater(boolean forceUpdate) {
            super(forceUpdate);
        }
        
        @Override
        protected Void doInBackground(Void... params) {
            taskCount = DEFAULT_TASK_COUNT;
            
            int progress = 0;
            boolean displayUnread = Controller.getInstance().onlyUnread();
            
            publishProgress(++progress); // Move progress forward
            if (selectArticlesForCategory) {
                Data.getInstance().updateArticles(categoryId, displayUnread, true, false, forceUpdate);
            } else {
                Data.getInstance().updateArticles(feedId, displayUnread, false, false, forceUpdate);
            }
            publishProgress(taskCount); // Move progress forward to 100%
            return null;
        }
    }
    
    @Override
    public void itemSelected(TYPE type, int selectedIndex, int oldIndex, int selectedId) {
        ListFragment secondPane = (ListFragment) getSupportFragmentManager().findFragmentById(R.id.article_view);
        if (isTablet && secondPane != null && secondPane.isInLayout()) {
            // Set the list item as checked
            // getListView().setItemChecked(selectedIndex, true);
            
            // Get the fragment instance
            ArticleFragment articleView = (ArticleFragment) getSupportFragmentManager().findFragmentById(
                    R.id.article_view);
            
            // Is the current selected ondex the same as the clicked? If so, there is no need to update
            if (articleView != null && selectedIndex == oldIndex)
                return;
            
            articleView = ArticleFragment.newInstance(selectedId, feedId, categoryId, selectArticlesForCategory,
                    ArticleFragment.ARTICLE_MOVE_DEFAULT);
            
            // Replace the old fragment with the new one
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.article_view, articleView);
            // Use a fade animation. This makes it clear that this is not a new "layer"
            // above the current, but a replacement
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            ft.commit();
            
        } else {
            // This is not a tablet - start a new activity
            Intent i = new Intent(context, ArticleActivity.class);
            i.putExtra(ArticleFragment.ARTICLE_ID, selectedId);
            i.putExtra(ArticleFragment.ARTICLE_FEED_ID, feedId);
            i.putExtra(FeedHeadlineListFragment.FEED_CAT_ID, categoryId);
            i.putExtra(FeedHeadlineListFragment.FEED_SELECT_ARTICLES, selectArticlesForCategory);
            i.putExtra(ArticleFragment.ARTICLE_MOVE, ArticleFragment.ARTICLE_MOVE_DEFAULT);
            startActivity(i);
        }
    }
    
}
