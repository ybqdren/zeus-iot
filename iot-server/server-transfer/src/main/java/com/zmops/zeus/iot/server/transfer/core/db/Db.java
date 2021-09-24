package com.zmops.zeus.iot.server.transfer.core.db;

import com.zmops.zeus.iot.server.transfer.core.job.CommandEntity;

import javax.management.openmbean.KeyAlreadyExistsException;
import java.io.Closeable;
import java.util.List;

/**
 * local storage for key/value.
 */
public interface Db extends Closeable {


    abstract KeyValueEntity get(String key);

    /**
     * get command by command id
     *
     * @param commandId
     * @return
     */
    CommandEntity getCommand(String commandId);

    /**
     * put command entity in db
     *
     * @param entity
     * @return
     */
    CommandEntity putCommand(CommandEntity entity);

    /**
     * store keyValue, if key has exists, throw exception.
     *
     * @param entity - key/value
     * @throws NullPointerException      key should not be null
     * @throws KeyAlreadyExistsException key already exists
     */
    void set(KeyValueEntity entity);

    /**
     * store keyValue, if key has exists, overwrite it.
     *
     * @param entity - key/value
     * @return null or old value which is overwritten.
     * @throws NullPointerException key should not be null.
     */
    KeyValueEntity put(KeyValueEntity entity);

    /**
     * remove keyValue by key.
     *
     * @param key - key
     * @return key/value
     * @throws NullPointerException key should not be null.
     */
    KeyValueEntity remove(String key);

    /**
     * search keyValue list by search key.
     *
     * @param searchKey - search keys.
     * @return key/value list
     * @throws NullPointerException search key should not be null.
     */
    List<KeyValueEntity> search(StateSearchKey searchKey);

    /**
     * search commands using ack status
     *
     * @param isAcked
     * @return
     */
    List<CommandEntity> searchCommands(boolean isAcked);

    /**
     * search one keyValue by search key
     *
     * @param searchKey - search key
     * @return null or keyValue
     */
    KeyValueEntity searchOne(StateSearchKey searchKey);

    /**
     * search one keyValue by fileName
     *
     * @param fileName
     * @return
     */
    KeyValueEntity searchOne(String fileName);

    /**
     * find all by prefix key.
     *
     * @param prefix - prefix string
     * @return list of k/v
     */
    List<KeyValueEntity> findAll(String prefix);
}