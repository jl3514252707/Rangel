# `Rangel` 仿真救援 安徽理工大学队伍代码

![avatar](https://www.aust.edu.cn/__local/8/7B/80/638D1607174C29D4831915BA71E_01FFC6E0_D2E6.png)

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
$ ./scripts/compile.sh
```

## 4. 执行

`Rangel`是使用ADF框架 (`adf-core-java`) 的RCRS (`rcrs-server`) 的队伍代码实现.

要运行 `Rangel`，首先必须运行 `rcrs-server`（有关如何下载、编译和运行 `rcrs-server` 的说明可在 <https://github.com/roborescue/rcrs-server> 获得）。

启动 `rcrs-server` 后，打开一个新的终端窗口并执行


预计算启动
```bash
$ ./scripts/precompute.sh
```

正常模式启动
```bash
$ ./scripts/launch.sh -all
```

## 5. 支持

要报告错误、建议改进或请求支持，请在 GitHub <https://github.com/jl3514252707/Rangel> 上提出issue.