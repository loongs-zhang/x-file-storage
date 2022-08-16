package cn.xuyanwu.spring.file.storage.platform;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.ftp.FtpConfig;
import cn.hutool.extra.ssh.Sftp;
import cn.xuyanwu.spring.file.storage.FileInfo;
import cn.xuyanwu.spring.file.storage.UploadPretreatment;
import cn.xuyanwu.spring.file.storage.exception.FileStorageRuntimeException;
import com.jcraft.jsch.SftpException;
import lombok.Getter;
import lombok.Setter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.function.Consumer;

/**
 * SFTP 存储
 */
@Getter
@Setter
public class SftpFileStorage implements FileStorage {

    /* 主机 */
    private String host;
    /* 端口，默认22 */
    private int port;
    /* 用户名 */
    private String user;
    /* 密码，默认空 */
    private String password;
    /* 编码，默认UTF-8 */
    private Charset charset;
    /* 连接超时时长，单位毫秒，默认10秒 */
    private long connectionTimeout;
    /* 存储平台 */
    private String platform;
    private String domain;
    private String basePath;
    private String storagePath;

    /**
     * 不支持单例模式运行，每次使用完了需要销毁
     */
    public Sftp getClient() {
//        Session session = JschUtil.getSession(host,port,user,password);
//        JschUtil.openSession(host,port,user,password,connectionTimeout);

        FtpConfig config = FtpConfig.create().setHost(host).setPort(port).setUser(user).setPassword(password).setCharset(charset)
                .setConnectionTimeout(connectionTimeout);
        return new Sftp(config,true);
    }


    @Override
    public void close() {
    }

    /**
     * 获取远程绝对路径
     */
    public String getAbsolutePath(String path) {
        return storagePath + path;
    }

    @Override
    public boolean save(FileInfo fileInfo,UploadPretreatment pre) {
        String newFileKey = basePath + fileInfo.getPath() + fileInfo.getFilename();
        fileInfo.setBasePath(basePath);
        fileInfo.setUrl(domain + newFileKey);

        Sftp client = getClient();
        try (InputStream in = pre.getFileWrapper().getInputStream()) {
            String path = getAbsolutePath(basePath + fileInfo.getPath());
            if(!client.exist(path)){
                client.mkDirs(path);
            }
            client.upload(path,fileInfo.getFilename(),in);

            byte[] thumbnailBytes = pre.getThumbnailBytes();
            if (thumbnailBytes != null) { //上传缩略图
                String newThFileKey = basePath + fileInfo.getPath() + fileInfo.getThFilename();
                fileInfo.setThUrl(domain + newThFileKey);
                client.upload(path,fileInfo.getThFilename(),new ByteArrayInputStream(thumbnailBytes));
            }

            return true;
        } catch (IOException | IORuntimeException e) {
            try {
                client.delFile(getAbsolutePath(newFileKey));
            } catch (IORuntimeException ignored) {
            }
            throw new FileStorageRuntimeException("文件上传失败！platform：" + platform + "，filename：" + fileInfo.getOriginalFilename(),e);
        } finally {
            IoUtil.close(client);
        }
    }

    @Override
    public boolean delete(FileInfo fileInfo) {
        try (Sftp client = getClient()) {
            if (fileInfo.getThFilename() != null) {   //删除缩略图
                client.delFile(getAbsolutePath(fileInfo.getBasePath() + fileInfo.getPath() + fileInfo.getThFilename()));
            }
            client.delFile(getAbsolutePath(fileInfo.getBasePath() + fileInfo.getPath() + fileInfo.getFilename()));
            return true;
        } catch (IORuntimeException e) {
            throw new FileStorageRuntimeException("文件删除失败！fileInfo：" + fileInfo,e);
        }
    }


    @Override
    public boolean exists(FileInfo fileInfo) {
        try (Sftp client = getClient()) {
            return client.exist(getAbsolutePath(fileInfo.getBasePath() + fileInfo.getPath() + fileInfo.getFilename()));
        } catch (IORuntimeException e) {
            throw new FileStorageRuntimeException("查询文件是否存在失败！fileInfo：" + fileInfo,e);
        }
    }

    @Override
    public void download(FileInfo fileInfo,Consumer<InputStream> consumer) {
        try (Sftp client = getClient();
             InputStream in = client.getClient().get(getAbsolutePath(fileInfo.getBasePath() + fileInfo.getPath() + fileInfo.getFilename()))) {
            consumer.accept(in);
        } catch (IOException | IORuntimeException | SftpException e) {
            throw new FileStorageRuntimeException("文件下载失败！platform：" + fileInfo,e);
        }
    }

    @Override
    public void downloadTh(FileInfo fileInfo,Consumer<InputStream> consumer) {
        if (StrUtil.isBlank(fileInfo.getThFilename())) {
            throw new FileStorageRuntimeException("缩略图文件下载失败，文件不存在！fileInfo：" + fileInfo);
        }

        try (Sftp client = getClient(); InputStream in = client.getClient().get(getAbsolutePath(fileInfo.getBasePath() + fileInfo.getPath()) + fileInfo.getThFilename())) {
            consumer.accept(in);
        } catch (IOException | IORuntimeException | SftpException e) {
            throw new FileStorageRuntimeException("缩略图文件下载失败！fileInfo：" + fileInfo,e);
        }
    }
}