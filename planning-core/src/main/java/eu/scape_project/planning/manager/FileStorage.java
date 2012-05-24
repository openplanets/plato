package eu.scape_project.planning.manager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.ejb.Stateful;
import javax.enterprise.context.ConversationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import eu.scape_project.planning.utils.FileUtils;

/**
 * A {@link IByteStreamStorage} which stores the data in the file system. 
 * The location of the files is defined by {@link #storagePath}
 * 
 * Note:
 *  - atm this storage handler shares the same scope as the plan being worked on
 *  - it is not defined what happens if multiple threads store data for the same pid
 *    This is not be a problem, as a bytestream is always related to one plan(which can only be accessed once at a time)
 *    and we do not have multiple threads altering the same bytestream 
 *       
 * 
 * @author Michael Kraxner
 *
 */
@Stateful
//@ConversationScoped
public class FileStorage implements Serializable, IByteStreamStorage {
	private static final long serialVersionUID = -2406172386311143101L;
	
	@Inject private Logger log;

	/**
	 * if not provided in filestorage.properties, it defaults to JBOSS_HOME/standalone/data/ps-filestore/"
	 */
	private String storagePath;
	
	/**
	 * File handle to storagePath
	 */
	private File storagePathFile;
	
	/**
	 * will be used as namespace for persistent identifiers, according to {@link https://wiki.duraspace.org/display/FEDORA35/Fedora+Identifiers}
	 */
	private String repositoryName;

	public FileStorage() {
	}
	

	@PostConstruct
	public void init() {
		// FIXME: better: define location in ... property file
		storagePath = System.getenv("JBOSS_HOME");
		if (storagePath != null) {
			storagePath = storagePath + "/standalone/data/ps-filestore/";  
		}
		if (storagePath != null) {
			storagePathFile = new File(storagePath);
			if (!storagePathFile.exists()) {
				if (!storagePathFile.mkdirs()) {
					log.error("failed to create storage directory !!! " + storagePath);
					storagePathFile = null;
					storagePath = null;
				}
			}
		} else {
			log.error("Failed to init file storage - JBOSS_HOME is not set.");
		}
		// for now we only consider one repository
		repositoryName = "plato";
	}
	
	@Override
	public String store(String pid, byte[] bytestream) throws StorageException {
		String objectId;
		if (pid == null) {
			// a new object
			objectId = UUID.randomUUID().toString();
			pid = repositoryName + ":" + objectId;
		} else {
			// we ignore the object's namespace
		    objectId = pid.substring(pid.indexOf(':'));
		}
		// we try to rename the file, if it already exists
		File file = new File(storagePathFile, objectId);
		File backup = null;
		if (file.exists()) {
			try {
				backup = File.createTempFile(file.getName(), "backup", storagePathFile);
				file.renameTo(backup);
			} catch (IOException e) {
				throw new StorageException("failed to create backup for: "  + pid, e);
			}
		}
		try {
			// write data to filesystem
			FileUtils.writeToFile(new ByteArrayInputStream(bytestream), new FileOutputStream(file));
			// data was stored successfully, backup is not needed any more
			if (backup != null) {
				backup.delete();
			}
		} catch (IOException e) {
			// try to restore old file
			if (backup != null) {
				if (backup.renameTo(file)) {
					backup = null;
				} else {
					throw new StorageException("failed to store digital object: " + pid + " and failed to restore backup!");
				}
			}
			throw new StorageException("failed to store digital object: " + pid, e);			
		}
		return pid;
		
	}

	@Override
	public byte[] load(String pid) throws StorageException {
		File file = getFile(pid);
		try {
			return FileUtils.inputStreamToBytes(new FileInputStream(file));
		} catch (IOException e) {
			throw new StorageException("failed to load data for persistent identifier: " + pid);
		}
	}
    @Override
	public void delete(String pid) throws StorageException {
		File file = getFile(pid);
		if (!file.delete()) {
			log.error("failed to delete object: " + pid);
		}
	}	
	
	private File getFile(String pid) throws StorageException {
		if ((pid == null) || (pid.isEmpty())) {
			throw new StorageException("provided persistent identifier is empty");
		}
		String objectId = pid.substring(pid.indexOf(':')+1);
		File file = new File(storagePathFile, objectId);
		if (file.exists()) {
			return file;
		} else {
			throw new StorageException("no object found for persistent identifier: " + pid);
		}		
	}

}