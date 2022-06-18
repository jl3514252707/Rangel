## 1. 环境要求

* Git
* OpenJDK Java 17
* Gradle

## 2. 下载

```bash
$ git clone https://github.com/jl3514252707/Rangel.git
```

## 3. 编译

```bash
$ ./gradlew clean
```

```bash
$ ./gradlew build
```

## 4. 执行

`Rangel`是使用ADF框架 (`adf-core-java`) 的RCRS (`rcrs-server`) 的队伍代码实现.

要运行 `Rangel`，首先必须运行 `rcrs-server`（有关如何下载、编译和运行 `rcrs-server` 的说明可在 <https://github.com/roborescue/rcrs-server> 获得）。

启动 `rcrs-server` 后，打开一个新的终端窗口并执行

```bash
$ cd script/
```

预计算启动

```bash
$ bash launch.sh -pre 1 -t 1,0,1,0,1,0 -local&&PID=$$;sleep 120;kill $PID
```

正常模式启动

```bash
$ ./launch.sh -all
```

## 5. 支持

要报告错误、建议改进或请求支持，请在 GitHub <https://github.com/jl3514252707/Rangel> 上提出issue.
