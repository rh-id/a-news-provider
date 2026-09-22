package m.co.rh.id.a_news_provider.test;

import android.app.PendingIntent;

import androidx.lifecycle.LiveData;
import androidx.work.Configuration;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.Operation;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkQuery;
import androidx.work.WorkRequest;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import kotlinx.coroutines.flow.Flow;

/**
 * Do-nothing WorkManager double for instrumented tests. Records enqueued one-time
 * work requests so tests can assert whether the fetch worker was enqueued, without
 * initializing the real WorkManager singleton (which is coupled to the first test
 * provider's executor for the whole process).
 */
public class FakeWorkManager extends WorkManager {

    private final List<WorkRequest> mEnqueuedRequests = new ArrayList<>();

    public List<WorkRequest> getEnqueuedWorkRequests() {
        return Collections.unmodifiableList(mEnqueuedRequests);
    }

    @Override
    public Configuration getConfiguration() {
        return null;
    }

    @Override
    public Operation enqueue(List<? extends WorkRequest> requests) {
        mEnqueuedRequests.addAll(requests);
        return null;
    }

    @Override
    public WorkContinuation beginWith(List<OneTimeWorkRequest> requests) {
        return null;
    }

    @Override
    public WorkContinuation beginUniqueWork(String uniqueWorkName, ExistingWorkPolicy existingWorkPolicy,
                                            List<OneTimeWorkRequest> requests) {
        return null;
    }

    @Override
    public Operation enqueueUniqueWork(String uniqueWorkName, ExistingWorkPolicy existingWorkPolicy,
                                       List<OneTimeWorkRequest> requests) {
        return null;
    }

    @Override
    public Operation enqueueUniquePeriodicWork(String uniqueWorkName, ExistingPeriodicWorkPolicy existingPeriodicWorkPolicy,
                                               PeriodicWorkRequest periodicWorkRequest) {
        return null;
    }

    @Override
    public Operation cancelWorkById(UUID id) {
        return null;
    }

    @Override
    public Operation cancelAllWorkByTag(String tag) {
        return null;
    }

    @Override
    public Operation cancelUniqueWork(String uniqueWorkName) {
        return null;
    }

    @Override
    public Operation cancelAllWork() {
        return null;
    }

    @Override
    public PendingIntent createCancelPendingIntent(UUID id) {
        return null;
    }

    @Override
    public Operation pruneWork() {
        return null;
    }

    @Override
    public LiveData<Long> getLastCancelAllTimeMillisLiveData() {
        return null;
    }

    @Override
    public ListenableFuture<Long> getLastCancelAllTimeMillis() {
        return null;
    }

    @Override
    public LiveData<WorkInfo> getWorkInfoByIdLiveData(UUID id) {
        return null;
    }

    @Override
    public Flow<WorkInfo> getWorkInfoByIdFlow(UUID id) {
        return null;
    }

    @Override
    public ListenableFuture<WorkInfo> getWorkInfoById(UUID id) {
        return null;
    }

    @Override
    public LiveData<List<WorkInfo>> getWorkInfosByTagLiveData(String tag) {
        return null;
    }

    @Override
    public Flow<List<WorkInfo>> getWorkInfosByTagFlow(String tag) {
        return null;
    }

    @Override
    public ListenableFuture<List<WorkInfo>> getWorkInfosByTag(String tag) {
        return null;
    }

    @Override
    public LiveData<List<WorkInfo>> getWorkInfosForUniqueWorkLiveData(String uniqueWorkName) {
        return null;
    }

    @Override
    public Flow<List<WorkInfo>> getWorkInfosForUniqueWorkFlow(String uniqueWorkName) {
        return null;
    }

    @Override
    public ListenableFuture<List<WorkInfo>> getWorkInfosForUniqueWork(String uniqueWorkName) {
        return null;
    }

    @Override
    public LiveData<List<WorkInfo>> getWorkInfosLiveData(WorkQuery workQuery) {
        return null;
    }

    @Override
    public Flow<List<WorkInfo>> getWorkInfosFlow(WorkQuery workQuery) {
        return null;
    }

    @Override
    public ListenableFuture<List<WorkInfo>> getWorkInfos(WorkQuery workQuery) {
        return null;
    }

    @Override
    public ListenableFuture<UpdateResult> updateWork(WorkRequest request) {
        return null;
    }
}
