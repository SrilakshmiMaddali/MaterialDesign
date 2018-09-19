package com.example.xyzreader.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.UpdaterService;
import com.squareup.picasso.Picasso;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = ArticleListActivity.class.getSimpleName();
    private static final String EXTRA_ID = "article selected extra id";
    private static final String RV_POSITION ="rv position";

    @BindView(R.id.swipe_refresh_layout)
    SwipeRefreshLayout mSwipeRefreshLayout;
    @BindView(R.id.empty_view)
    TextView mEmptyView;
    @BindView(R.id.recycler_view)
    RecyclerView mRecyclerView;
    private int recuclerViewPosition;

    @SuppressLint("SimpleDateFormat")
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    @SuppressLint("SimpleDateFormat")
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_list);
        ButterKnife.bind(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);

        mSwipeRefreshLayout.setOnRefreshListener(() -> refresh());

        getSupportLoaderManager().initLoader(0, null, this);

        mRefreshingReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                    mSwipeRefreshLayout.setRefreshing(false);

                }
            }
        };

        if (savedInstanceState == null) {
            refresh();
        } else {
            recuclerViewPosition = savedInstanceState.getInt(RV_POSITION);
        }
    }

    private void refresh() {
        mSwipeRefreshLayout.setRefreshing(true);
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mRecyclerView != null) {
            StaggeredGridLayoutManager manager = (StaggeredGridLayoutManager) mRecyclerView.getLayoutManager();
            if (manager != null && mRecyclerView.getAdapter() != null) {
                int[] positions = new int[manager.getSpanCount()];
                manager.findFirstCompletelyVisibleItemPositions(positions);
                outState.putInt(RV_POSITION,positions[0]);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mRefreshingReceiver);
    }

    private boolean mIsRefreshing = false;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }
    };

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (cursor.getCount() > 0) {
           // mEmptyView.setVisibility(View.GONE);
        } else {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = Objects.requireNonNull(cm).getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) {
                mEmptyView.setVisibility(View.VISIBLE);
                Toast.makeText(this, "No netwrok connection", Toast.LENGTH_SHORT);
            }
        }
        Adapter adapter = new Adapter(cursor);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);
        int columnCount = getResources().getInteger(R.integer.list_column_count);
        StaggeredGridLayoutManager lm =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(lm);
        mRecyclerView.scrollToPosition(recuclerViewPosition);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;
        private final ConstraintSet mConstraintSet;
        private final int DEFAULT_HEIGHT = 3;

        public Adapter(Cursor cursor) {
            mCursor = cursor;
            mConstraintSet = new ConstraintSet();
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(ArticleListActivity.this, ArticleDetailActivity.class);
                    long id = getItemId(vh.getAdapterPosition());
                    intent.putExtra(EXTRA_ID, id);
                    startActivity(intent);
                }
            });
            return vh;
        }

        private Date parsePublishedDate() {
            try {
                String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
                return simpleDateFormat.parse(date);
            } catch (ParseException ex) {
                Log.e(TAG, ex.getMessage());
                Log.i(TAG, "passing today's date");
                return new Date();
            }
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            mCursor.moveToPosition(position);
            holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            Date publishedDate = parsePublishedDate();
            String author = mCursor.getString(ArticleLoader.Query.AUTHOR);
            holder.authorTextView.setText(String.format(getString(R.string.by_author), author));
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {

                holder.dateTextView.setText(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString());
            } else {
                holder.dateTextView.setText(
                        outputFormat.format(publishedDate));
            }
            String url = mCursor.getString(ArticleLoader.Query.THUMB_URL);
            //aspect ratio as single number, as from query
            float aspectRatio = mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO);

            //aspect ratio as proportion width:height
            String ratio = String.format(Locale.ENGLISH, "%.2f:%d", aspectRatio, DEFAULT_HEIGHT);
            mConstraintSet.clone(holder.constraintLayout);
            mConstraintSet.setDimensionRatio(holder.thumbnailView.getId(), ratio);
            mConstraintSet.applyTo(holder.constraintLayout);
            Picasso.get().load(url).placeholder(R.drawable.ic_library_books_black_24dp)
                    .error(R.drawable.ic_library_books_black_24dp).into(holder.thumbnailView);
        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.thumbnail)
        ImageView thumbnailView;
        @BindView(R.id.article_title)
        TextView titleView;
        @BindView(R.id.article_date)
        TextView dateTextView;
        @BindView(R.id.article_author)
        TextView authorTextView;
        @BindView(R.id.list_item_constraint_layout)
        ConstraintLayout constraintLayout;

        ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, itemView);
        }
    }
}
