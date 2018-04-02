###################################################
#
# 将编译后的jar包 和配置信息 运行脚本
# 打包成 .tar.gz 并上传至服务器
#
###################################################
#!/bin/bash


mvn clean package -Pwithjar -Dmaven.test.skip=true

#部署服务器地址
deploy_server=bigdata-dev

#版本
version=1.0
#部署服务器目录 real name authentication
deploy_path=/home/hdfs/deve
#用户名
name=hadoop

DIR=$(cd $(dirname $0);pwd)
echo $DIR
#
#模块
module=service-restful
scp  ${DIR}/${module}/target/${module}-${version}.tar.gz ${name}@${deploy_server}:$deploy_path
#
ssh ${name}@$deploy_server "cd $deploy_path && tar -xzvf ${module}-${version}.tar.gz"


