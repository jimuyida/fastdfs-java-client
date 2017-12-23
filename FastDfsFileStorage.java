package com.haulmont.cuba.core.app.filestorage.jmyida;

import com.anniweiya.fastdfs.FastDFSTemplate;
import com.anniweiya.fastdfs.FastDfsInfo;
import com.anniweiya.fastdfs.exception.FastDFSException;
import com.haulmont.cuba.core.*;
import com.haulmont.cuba.core.app.FileStorageAPI;
import com.haulmont.cuba.core.app.filestorage.amazon.util.HttpUtils;
import com.haulmont.cuba.core.entity.FileDescriptor;
import com.haulmont.cuba.core.global.FileStorageException;
import com.haulmont.cuba.core.global.Metadata;
import com.jmyida.modelplatform.entity.FastDFSFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.csource.common.MyException;
import org.csource.fastdfs.*;
import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static com.haulmont.bali.util.Preconditions.checkNotNullArgument;

public class FastDfsFileStorage implements FileStorageAPI/* ,BeanPostProcessor*/ {

    @Inject
    Metadata metadata;
    @Inject
    Persistence persistence;

    @Inject
    FastDFSTemplate fastDFSTemplate;

    @Override
    public long saveStream(FileDescriptor fileDescr, InputStream inputStream) throws FileStorageException {

        try {
            FastDfsInfo fastDfsInfo = fastDFSTemplate.upload(inputStream,fileDescr.getExtension(),fileDescr.getSize());
            /*//FastDFS的核心操作在于tracker处理上，所以此时需要定义tracker客户端
            TrackerClient trackerClient = new TrackerClient() ;
            //定义TrackerServer配置信息
            TrackerServer trackerServer = null;
            trackerServer = trackerClient.getConnection();
            //在整个FastDFS之中真正负责干活的就是storage
            StorageServer storageServer = null ;
            StorageClientStream storageClient = new StorageClientStream(trackerServer, storageServer) ;
            //len=0则表示删除成功，不成功返回2

            String[] files = storageClient.upload_file(inputStream,fileDescr.getExtension(),fileDescr,null);
            trackerServer.close();
            */

            Transaction tx = persistence.createTransaction();
            try {
                FastDFSFile fastDFSFile = metadata.create(FastDFSFile.class);
                fastDFSFile.setGroup(fastDfsInfo.getGroup());
                fastDFSFile.setFileName(fastDfsInfo.getPath());
                /*FileInfo fileInfo = storageClient.query_file_info(files[0],files[1]);
                fastDFSFile.setCrc((int) fileInfo.getCrc32());
                fastDFSFile.setFileSize(fileInfo.getFileSize());
                fastDFSFile.setSourceIpAddr(fileInfo.getSourceIpAddr());
                fastDFSFile.setCreateTimestamp(fileInfo.getCreateTimestamp());*/
                fastDFSFile.setFileDescriptorId(fileDescr.getId());
                persistence.getEntityManager().persist(fastDFSFile);
                tx.commit();
            } catch (Exception e){
                throw new FileStorageException(FileStorageException.Type.IO_EXCEPTION, "保存信息到FastDFS表出错");
            } finally {
                tx.end();
            }
        } catch (FastDFSException e) {
            String message = String.format("Could not save file %s. %s",
                    getFileName(fileDescr), e.getMessage());
            throw new FileStorageException(FileStorageException.Type.IO_EXCEPTION, message);
        }
        return fileDescr.getSize();
    }

    @Override
    public void saveFile(FileDescriptor fileDescr, byte[] data) throws FileStorageException {
        checkNotNullArgument(data, "File content is null");
        saveStream(fileDescr, new ByteArrayInputStream(data));
    }

    protected String getFileName(FileDescriptor fileDescriptor) {
        if (StringUtils.isNotBlank(fileDescriptor.getExtension())) {
            return fileDescriptor.getId().toString() + "." + fileDescriptor.getExtension();
        } else {
            return fileDescriptor.getId().toString();
        }
    }

    protected String getInputStreamContent(HttpUtils.HttpResponse httpResponse) {
        try {
            return IOUtils.toString(httpResponse.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void removeFile(FileDescriptor fileDescr) throws FileStorageException {
        try {
            //FastDFS的核心操作在于tracker处理上，所以此时需要定义tracker客户端
            TrackerClient trackerClient = new TrackerClient() ;
            //定义TrackerServer配置信息
            TrackerServer trackerServer = null;
            trackerServer = trackerClient.getConnection();
            //在整个FastDFS之中真正负责干活的就是storage
            StorageServer storageServer = null ;
            StorageClient1 storageClient = new StorageClient1(trackerServer, storageServer) ;
            //len=0则表示删除成功，不成功返回2
            int len = storageClient.delete_file1("group1/M00/00/00/wKgUiVn8g26AbQWZAACfBHb9Wcw840.jpg") ;
            System.err.println(len);
            trackerServer.close();
        } catch (IOException e) {
            String message = String.format("Could not save file %s. %s",
                    getFileName(fileDescr), e.getMessage());
            throw new FileStorageException(FileStorageException.Type.IO_EXCEPTION, message);
        } catch (MyException e) {
            String message = String.format("Could not save file %s. %s",
                    getFileName(fileDescr), e.getMessage());
            throw new FileStorageException(FileStorageException.Type.IO_EXCEPTION, message);
        }
    }

    @Override
    public InputStream openStream(FileDescriptor fileDescr) throws FileStorageException {
        FastDFSFile fastDFSFile ;
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            Query q = em.createQuery("select u from modelplatform$FastDFSFile u where u.fileDescriptorId = ?1");
            q.setParameter(1, fileDescr.getId());

            q.setView(FastDFSFile.class,"view");
            fastDFSFile = (FastDFSFile) q.getSingleResult();
            tx.commit();
        }

        if (fastDFSFile == null) {
            throw new FileStorageException(FileStorageException.Type.FILE_NOT_FOUND,
                    "File not found" + getFileName(fileDescr));
        }

        try {

            byte[] buf = fastDFSTemplate.loadFile(fastDFSFile.getGroup(),fastDFSFile.getFileName());
            return new ByteArrayInputStream(buf);

            /*
            //FastDFS的核心操作在于tracker处理上，所以此时需要定义tracker客户端
            TrackerClient trackerClient = new TrackerClient() ;
            //定义TrackerServer配置信息
            TrackerServer trackerServer = null;
            trackerServer = trackerClient.getConnection();
            //在整个FastDFS之中真正负责干活的就是storage
            StorageServer storageServer = null ;
            StorageClient1 storageClient = new StorageClient1(trackerServer, storageServer) ;
            //len=0则表示删除成功，不成功返回2
            byte[] buf = storageClient.download_file(fastDFSFile.getGroup(),fastDFSFile.getFileName());
            trackerServer.close();
            return new ByteArrayInputStream(buf);*/
        } catch (FastDFSException e) {
            String message = String.format("Could not save file %s. %s",
                    getFileName(fileDescr), e.getMessage());
            throw new FileStorageException(FileStorageException.Type.IO_EXCEPTION, message);
        }

    }

    @Override
    public byte[] loadFile(FileDescriptor fileDescr) throws FileStorageException {
        InputStream inputStream = openStream(fileDescr);
        try {
            return IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.Type.IO_EXCEPTION, fileDescr.getId().toString(), e);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    @Override
    public boolean fileExists(FileDescriptor fileDescr) throws FileStorageException {
        FastDFSFile fastDFSFile ;
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            Query q = em.createQuery("select u from modelplatform$FastDFSFile u where u.fileDescriptorId = ?1");
            q.setParameter(1, fileDescr.getId());

            q.setView(FastDFSFile.class,"view");
            fastDFSFile = (FastDFSFile) q.getSingleResult();
            tx.commit();
        }

        if (fastDFSFile == null) {
            return false;
        }


        try {
            FileInfo fileInfo = fastDFSTemplate.query_file_info(fastDFSFile.getGroup(),fastDFSFile.getFileName());
            if (fileInfo == null || fileInfo.getFileSize() <= 0) {
                return false;
            }

            return true;

            /*//FastDFS的核心操作在于tracker处理上，所以此时需要定义tracker客户端
            TrackerClient trackerClient = new TrackerClient() ;
            //定义TrackerServer配置信息
            TrackerServer trackerServer = null;
            trackerServer = trackerClient.getConnection();
            //在整个FastDFS之中真正负责干活的就是storage
            StorageServer storageServer = null ;
            StorageClientStream storageClient = new StorageClientStream(trackerServer, storageServer) ;
            //len=0则表示删除成功，不成功返回2
            trackerServer.close();

            FileInfo fileInfo = storageClient.query_file_info(fastDFSFile.getGroup(),fastDFSFile.getFileName());
            if (fileInfo == null || fileInfo.getFileSize() <= 0) {
                return false;
            }

            return true;*/


        } catch (FastDFSException e) {
            String message = String.format("Could not save file %s. %s",
                    getFileName(fileDescr), e.getMessage());
            throw new FileStorageException(FileStorageException.Type.IO_EXCEPTION, message);
        }
    }

/*
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        System.out.println(beanName);
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (ClientGlobal.getG_tracker_group() != null) {
            return bean;
        }
        ClientGlobal.setG_anti_steal_token(fastDFSConfig.getAntiStealToken());
        ClientGlobal.setG_charset(fastDFSConfig.getCharset());
        ClientGlobal.setG_connect_timeout(fastDFSConfig.getConnectTimeout());
        ClientGlobal.setG_network_timeout(fastDFSConfig.getNetworkTimeout());
        ClientGlobal.setG_secret_key(fastDFSConfig.getSecretKey());
        try {
            ClientGlobal.initByTrackers(fastDFSConfig.getTrackerServers());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (MyException e) {
            e.printStackTrace();
        }
        ClientGlobal.setG_tracker_http_port(fastDFSConfig.getTrackerHttpPort());
        return bean;
    }*/
}
