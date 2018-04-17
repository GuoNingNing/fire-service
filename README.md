fire-service是基于Spray的Restfull服务框架，可基于此工程，快速搭建自己的RestFull服务

它支持一下功能：
    
* Post方式提交任务
* 程序运行状态监控

  
# 部署
    进入fire-service目录，执行 mvn clean package
    Copy service-restful/target/service-restful-1.0.tar.gz到部署服务器并解压
    执行 bash restfull.sh start 启动服务
    执行 bash restfull.sh stop  停止服务 
    执行 bash restfull.sh status  查看状态 
    执行 bash restfull.sh restart  重启服务
    
# 接口说明
       
    
#### 提交任务
    
    通过Post方式提交任务到集群，并将任务加入监控
    任务必须是
            1、基于fire-spark开发的
            2、按照fire-spark-demo进行打包
            3、在application.conf配置Spark工程部署目录app.manager.path = "/home/hdfs/deve/sparkapp"
    
```bash
 curl -XPOST -H "Content-Type:application/json" 'http://localhost:9200/app/submit' -d '{
        "command":"applog-collect-1.0/run.sh",
        "args":["conf/online/applogcollect.properties"]
}'
```

#### 查看监控列表及App运行信息

```bash
curl http://localhost:9200/app/monitors
```

#### 杀掉一个APP并从监控列表移除

```bash
curl http://localhost:9200/app/kill/{appId}
```
