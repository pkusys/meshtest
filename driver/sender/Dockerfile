FROM python:3.10-slim
WORKDIR /app
ADD . /app
RUN mv /app/sources.list /etc/apt/sources.list
RUN rm -rf /etc/apt/sources.list.d
RUN apt-get update && apt-get install -y net-tools
RUN apt-get install -y iputils-ping curl tcpdump vim procps
RUN pip3 config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple
RUN pip3 install -r requirements.txt

ENTRYPOINT [ "python3" ]