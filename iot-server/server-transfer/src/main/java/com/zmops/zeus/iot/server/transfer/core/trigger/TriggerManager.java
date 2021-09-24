package com.zmops.zeus.iot.server.transfer.core.trigger;

import com.zmops.zeus.iot.server.transfer.api.Trigger;
import com.zmops.zeus.iot.server.transfer.common.AbstractDaemon;
import com.zmops.zeus.iot.server.transfer.conf.JobProfile;
import com.zmops.zeus.iot.server.transfer.conf.TransferConfiguration;
import com.zmops.zeus.iot.server.transfer.conf.TransferConstants;
import com.zmops.zeus.iot.server.transfer.conf.TriggerProfile;
import com.zmops.zeus.iot.server.transfer.core.TransferManager;
import com.zmops.zeus.iot.server.transfer.core.db.TriggerProfileDb;
import com.zmops.zeus.iot.server.transfer.core.job.JobWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.zmops.zeus.iot.server.transfer.conf.JobConstants.JOB_ID;
import static com.zmops.zeus.iot.server.transfer.conf.JobConstants.TRIGGER_ONLY_ONE_JOB;
import static com.zmops.zeus.iot.server.transfer.conf.TransferConstants.DEFAULT_TRIGGER_MAX_RUNNING_NUM;
import static com.zmops.zeus.iot.server.transfer.conf.TransferConstants.TRIGGER_MAX_RUNNING_NUM;

/**
 * 文件变动监听触发器管理
 *
 * @editor nantian
 */
public class TriggerManager extends AbstractDaemon {

    private static final Logger LOGGER = LoggerFactory.getLogger(TriggerManager.class);

    public static final int JOB_CHECK_INTERVAL = 1;

    private final TransferManager  manager;
    private final TriggerProfileDb triggerProfileDB;

    private final ConcurrentHashMap<String, Trigger> triggerMap;

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, JobProfile>> triggerJobMap;

    private final int triggerFetchInterval;
    private final int maxRunningNum;

    public TriggerManager(TransferManager manager, TriggerProfileDb triggerProfileDb) {

        TransferConfiguration conf = TransferConfiguration.getAgentConf();

        this.manager = manager;
        this.triggerProfileDB = triggerProfileDb;
        this.triggerMap = new ConcurrentHashMap<>();
        this.triggerJobMap = new ConcurrentHashMap<>();
        this.triggerFetchInterval = conf.getInt(TransferConstants.TRIGGER_FETCH_INTERVAL, TransferConstants.DEFAULT_TRIGGER_FETCH_INTERVAL);
        this.maxRunningNum = conf.getInt(TRIGGER_MAX_RUNNING_NUM, DEFAULT_TRIGGER_MAX_RUNNING_NUM);
    }

    /**
     * submit trigger profile.
     *
     * @param triggerProfile - trigger profile
     */
    public boolean addTrigger(TriggerProfile triggerProfile) {
        try {
            Trigger trigger   = new DirectoryTrigger();
            String  triggerId = triggerProfile.get(JOB_ID);

            if (triggerMap.containsKey(triggerId)) {
                deleteTrigger(triggerId);
                LOGGER.warn("trigger {} is running, stop it", triggerId);
            }

            triggerMap.put(triggerId, trigger);
            trigger.init(triggerProfile);
            trigger.run();

        } catch (Exception ex) {
            LOGGER.error("exception caught", ex);
            return false;
        }
        return true;
    }


    public Trigger getTrigger(String triggerId) {
        return triggerMap.get(triggerId);
    }

    public boolean submitTrigger(TriggerProfile triggerProfile) {
        // make sure all required key exists.
        if (!triggerProfile.allRequiredKeyExist() || this.triggerMap.size() > maxRunningNum) {
            LOGGER.error("trigger {} not all required key exists or size {} exceed {}", triggerProfile.toJsonStr(), this.triggerMap.size(), maxRunningNum);
            return false;
        }
        triggerProfileDB.storeTrigger(triggerProfile);
        addTrigger(triggerProfile);
        return true;
    }


    private Runnable jobFetchThread() {
        return () -> {
            while (isRunnable()) {
                try {
                    triggerMap.forEach((s, trigger) -> {
                        JobProfile profile = trigger.fetchJobProfile();

                        if (profile != null) {
                            TriggerProfile triggerProfile = trigger.getTriggerProfile();

                            if (triggerProfile.getBoolean(TRIGGER_ONLY_ONE_JOB, false)) {
                                deleteRelatedJobs(triggerProfile.getTriggerId());
                            }

                            manager.getJobManager().submitJobProfile(profile);
                            addToTriggerMap(profile.get(JOB_ID), profile);
                        }
                    });
                    TimeUnit.SECONDS.sleep(triggerFetchInterval);
                } catch (Exception ignored) {
                    LOGGER.info("ignored Exception ", ignored);
                }
            }

        };
    }

    /**
     * delete jobs generated by the trigger
     *
     * @param triggerId
     */
    private void deleteRelatedJobs(String triggerId) {
        LOGGER.info("start to delete related jobs in triggerId {}", triggerId);
        ConcurrentHashMap<String, JobProfile> jobProfiles = triggerJobMap.get(triggerId);

        if (jobProfiles != null) {
            LOGGER.info("trigger can only run one job, stop the others {}", jobProfiles.keySet());
            jobProfiles.keySet().forEach(this::deleteJob);
            triggerJobMap.remove(triggerId);
        }
    }

    private void deleteJob(String jobInstanceId) {
        manager.getJobManager().deleteJob(jobInstanceId);
    }


    private Runnable jobCheckThread() {
        return () -> {
            while (isRunnable()) {
                try {
                    triggerJobMap.forEach((s, jobProfiles) -> {
                        for (String jobId : jobProfiles.keySet()) {
                            Map<String, JobWrapper> jobs = manager.getJobManager().getJobs();
                            if (jobs.get(jobId) == null) {
                                triggerJobMap.remove(jobId);
                            }
                        }
                    });
                    TimeUnit.MINUTES.sleep(JOB_CHECK_INTERVAL);
                } catch (Exception ignored) {
                    LOGGER.info("ignored Exception ", ignored);
                }
            }

        };
    }

    /**
     * need to put profile in triggerJobMap
     *
     * @param triggerId
     * @param profile
     */
    private void addToTriggerMap(String triggerId, JobProfile profile) {
        ConcurrentHashMap<String, JobProfile> tmpList     = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, JobProfile> jobWrappers = triggerJobMap.putIfAbsent(triggerId, tmpList);

        if (jobWrappers == null) {
            jobWrappers = tmpList;
        }

        jobWrappers.putIfAbsent(profile.getInstanceId(), profile);
    }

    /**
     * delete trigger by trigger profile.
     *
     * @param triggerId - trigger profile.
     */
    public boolean deleteTrigger(String triggerId) {
        LOGGER.info("delete trigger {}", triggerId);
        Trigger trigger = triggerMap.remove(triggerId);

        if (trigger != null) {
            deleteRelatedJobs(triggerId);
            trigger.destroy();
            // delete trigger from db
            triggerProfileDB.deleteTrigger(triggerId);
            return true;
        }
        LOGGER.warn("cannot find trigger {}", triggerId);
        return false;
    }


    /**
     * init all triggers when daemon started.
     */
    private void initTriggers() throws Exception {
        // fetch all triggers from db
        List<TriggerProfile> profileList = triggerProfileDB.getTriggers();

        for (TriggerProfile profile : profileList) {
            addTrigger(profile);
        }
    }

    private void stopTriggers() {
        triggerMap.forEach((s, trigger) -> {
            trigger.destroy();
        });
    }

    @Override
    public void start() throws Exception {
        initTriggers();
        submitWorker(jobFetchThread());
        submitWorker(jobCheckThread());
    }


    @Override
    public void stop() {
        // stop all triggers
        stopTriggers();
    }


}