# fastdfs-java-client

这是一个fastdfs 的java连接客户端，支持连接池，集成进入cuba-platform中。

1. 修改自https://github.com/anniweiya/fastdfs-client
2. Cuba中集成

    1. clone 到文件夹modules\fastdfs
    2. 修改settings.gradle
    ```
    project(':fastdfs').projectDir = new File(settingsDir, 'modules/fastdfs')
    ```
    3. 修改build.gradle
    ```
    def guiModule = project(':app-gui')
    def webModule = project(':app-web')
    def fastDFSModule = project(':fastdfs')

    ....

    configure([globalModule, coreModule, guiModule, webModule, fastDFSModule]) {
    apply(plugin: 'java')
    apply(plugin: 'maven')
    apply(plugin: 'idea')
    apply(plugin: 'cuba')

    .....

    configure(fastDFSModule) {
        task enhance(type: CubaEnhancing)
        dependencies {
            // https://mvnrepository.com/artifact/org.apache.commons/commons-pool2
            compile group: 'org.apache.commons', name: 'commons-pool2', version: '2.4.3'
        }
    }

    ....

    configure(coreModule) {

    configurations {
        jdbc
        dbscripts
    }

    dependencies {
        compile(globalModule,fastDFSModule)
        provided(servletApi)
        jdbc(mysql)

    ...


    ```
    4. 修改core中的spring.xml
    ```xml
    <bean name="cuba_FileStorage"
          class="com.haulmont.cuba.core.app.filestorage.jmyida.FastDfsFileStorage"/>
          
    <bean id="fastDFSFactory" class="com.anniweiya.fastdfs.FastDFSTemplateFactory" init-method="init">
            <!--连接超时的时限，单位为秒-->
            <property name="g_connect_timeout" value="60"/>
            <!--网络超时的时限，单位为秒-->
            <property name="g_network_timeout" value="80"/>
            <!--防盗链配置-->
            <property name="g_anti_steal_token" value="true"/>
            <property name="g_secret_key" value="FastDFS1234567890"/>
            <property name="poolConfig">
                <bean class="com.anniweiya.fastdfs.pool.PoolConfig">
                    <!--池的大小-->
                    <property name="maxTotal" value="100"/>
                    <!--连接池中最大空闲的连接数-->
                    <property name="maxIdle" value="10"/>
                </bean>
            </property>
            <!--tracker的配置 ","逗号分隔-->
            <property name="tracker_servers" value="192.168.0.11:22122"/>
            <!--HTTP访问服务的端口号-->
            <property name="g_tracker_http_port" value="8080"/>
            <!--nginx的对外访问地址，如果没有端口号，将取g_tracker_http_port配置的端口号 ","逗号分隔-->
            <property name="nginx_address" value="127.0.0.1:8080"/>
    </bean>

    <!--注入模板类-->
    <bean id="fastDFSTemplate" class="com.anniweiya.fastdfs.FastDFSTemplate">
        <constructor-arg ref="fastDFSFactory"/>
    </bean>
    ```

3. 把FastDfsFileStorage.java复制到com.haulmont.cuba.core.app.filestorage.jmyida
4. 创建一个Entity名字：modelplatform$FastDFSFile
参考cuba-fastdfs-entity.png
![](https://github.com/jimuyida/fastdfs-java-client/blob/master/cuba-fastdfs-entity.png)

5. 测试
在界面上拖放一个上传控件,然后java代码如下：
```java

    @Inject
    private FileUploadingAPI fileStorageAPI;
    @Inject
    private FileUploadField upload;
    @Inject
    private DataSupplier dataSupplier;
    @Inject
    private FileUploadingAPI fileUploadingAPI;

    @Override
    public void init(Map<String, Object> params) {
        upload.addFileUploadSucceedListener(event -> {
            FileDescriptor fd = upload.getFileDescriptor();
            try {
                // save file to FileStorage
                fileUploadingAPI.putFileIntoStorage(upload.getFileId(), fd);
            } catch (FileStorageException e) {
                throw new RuntimeException("Error saving file to FileStorage", e);
            }
            // save file descriptor to database
            dataSupplier.commit(fd);
            showNotification("文件上传成功: " + upload.getFileName(), NotificationType.HUMANIZED);
        });

        upload.addFileUploadErrorListener(event ->
                showNotification("File upload error", NotificationType.HUMANIZED));
    }
``