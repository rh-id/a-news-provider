package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.provider.FileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class LogPage extends StatefulView<Activity> implements RequireComponent<Provider> {
    private static final String TAG = LogPage.class.getName();

    @NavInject
    private AppBarSV mAppBarSV;

    private transient Provider mSvProvider;

    public LogPage() {
        mAppBarSV = new AppBarSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.page_log,
                container, false);
        ViewGroup rootLayout = view.findViewById(R.id.root_layout);
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        mAppBarSV.setTitle(activity.getString(R.string.log_file));
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        ProgressBar progressBar = view.findViewById(R.id.progress_circular);
        View noRecord = view.findViewById(R.id.no_record);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        LogLineRecyclerViewAdapter adapter = new LogLineRecyclerViewAdapter();
        recyclerView.setAdapter(adapter);
        FileHelper fileHelper = mSvProvider.get(FileHelper.class);
        File logFile = fileHelper.getLogFile();
        FloatingActionButton fabClear = view.findViewById(R.id.fab_clear);
        FloatingActionButton fabShare = view.findViewById(R.id.fab_share);
        fabShare.setOnClickListener(v -> {
            try {
                UiUtils.shareFile(activity, logFile, activity.getString(R.string.share_log_file));
            } catch (Throwable e) {
                mSvProvider.get(ILogger.class)
                        .e(TAG, activity.getString(R.string.error_sharing_log_file), e);
            }
        });
        BehaviorSubject<File> subject = BehaviorSubject.createDefault(logFile);
        fabClear.setOnClickListener(view1 -> {
            fileHelper.clearLogFile();
            mSvProvider.get(ILogger.class).i(TAG, activity.getString(R.string.log_file_deleted));
            mSvProvider.get(Handler.class)
                    .post(() -> subject.onNext(logFile));
        });
        mSvProvider.get(RxDisposer.class).add("readLogFile",
                subject.
                        observeOn(Schedulers.from(mSvProvider.get(ExecutorService.class)))
                        .map(file -> {
                            if (!file.exists()) {
                                return new ArrayList<String>();
                            } else {
                                List<String> lines = new ArrayList<>();
                                try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
                                    String line;
                                    while ((line = bufferedReader.readLine()) != null) {
                                        lines.add(line);
                                    }
                                }
                                return lines;
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(lines -> {
                            progressBar.setVisibility(View.GONE);
                            adapter.setItems(lines);
                            if (lines.isEmpty()) {
                                noRecord.setVisibility(View.VISIBLE);
                                recyclerView.setVisibility(View.GONE);
                                fabShare.setVisibility(View.GONE);
                                fabClear.setVisibility(View.GONE);
                            } else {
                                noRecord.setVisibility(View.GONE);
                                recyclerView.setVisibility(View.VISIBLE);
                                recyclerView.post(() -> {
                                    int lastPos = adapter.getItemCount() - 1;
                                    if (lastPos >= 0) {
                                        recyclerView.smoothScrollToPosition(lastPos);
                                    }
                                });
                                fabShare.setVisibility(View.VISIBLE);
                                fabClear.setVisibility(View.VISIBLE);
                            }
                        }));

        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }
}
