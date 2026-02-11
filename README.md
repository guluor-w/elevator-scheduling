# elevator-scheduling
本项目实现了一个 **6 部电梯** 的并发调度与运行模拟器，支持两类输入请求：

* **乘客请求**（`PersonRequest`）：乘客从某层到某层，带优先级
* **临时调度请求**（`ScheRequest`）：对指定电梯下发“临时调度任务”（改变速度并将电梯调度至目标楼层，强制清空乘客）

项目基于课程库 `com.oocourse.elevator3`，通过 `TimableOutput` 输出标准事件日志。



## 功能概览

* 6 个电梯线程并发运行（`ElevatorThread`，id=1..6）
* 1 个输入线程读取请求（`InputThread`）
* 1 个调度线程将乘客分配给电梯（`DispatchThread`）
* 全程通过 **阻塞队列 + wait/notify** 实现线程间协作
* 支持 **临时调度（SCHE）**：电梯以指定速度运行到指定楼层，开门后清空电梯内所有乘客；未到目的地的乘客会被“转移”为新的待调度乘客


## 项目结构

* `MainClass.java`：程序入口，初始化 6 个电梯队列与线程，启动输入与调度线程
* `InputThread.java`：从 `ElevatorInput(System.in)` 读取请求

  * `PersonRequest` → 封装为 `Passenger` 放入全局乘客队列 `PassengerQueue`
  * `ScheRequest` → 投递到指定电梯的 `ElevatorRequestQueue.haveTempTask()`（并置该电梯为 silent）
* `DispatchThread.java`：从 `PassengerQueue` 取乘客，**轮询**分配给非 silent 的电梯队列，并打印 `RECEIVE`
* `ElevatorThread.java`：单部电梯的状态机运行逻辑（移动/开门/上下客/临时调度/结束）
* `PassengerQueue.java`：全局乘客队列（调度线程的输入），支持结束标记与阻塞 `poll()`
* `ElevatorRequestQueue.java`：单电梯请求队列（乘客 + 临时调度任务），包含选取任务/选取上车乘客的策略
* `Passenger.java`：对 `PersonRequest` 的包装（继承 `Request`），并允许更新 `fromFloor`（用于临时调度后的“转移乘客”）
* `Trans.java`：楼层字符串与内部整数表示的转换工具
* `TestMain.java`：本地测试入口，支持带时间戳的输入回放（见下文）


## 运行与依赖

### 依赖

需要 `oocourse.elevator3` 相关库（包含 `ElevatorInput / Request / PersonRequest / ScheRequest / TimableOutput` 等）。将其 jar 放到 classpath 中即可。

### 编译

```bash
javac -cp .:path/to/oocourse-elevator3.jar *.java
```

Windows：

```bat
javac -cp .;path\to\oocourse-elevator3.jar *.java
```

### 运行

```bash
java -cp .:path/to/oocourse-elevator3.jar MainClass
```

## 输入与输出

输入由库 `ElevatorInput` 解析，支持：

* `PersonRequest`
* `ScheRequest`

> 具体文本格式以 `oocourse.elevator3` 的输入规范为准。

项目会输出以下关键事件（格式严格由代码打印）：

* `RECEIVE-<personId>-<elevatorId>`：调度线程将乘客分配给某部电梯
* `ARRIVE-<floor>-<elevatorId>`：电梯到达某层
* `OPEN-<floor>-<elevatorId>` / `CLOSE-<floor>-<elevatorId>`：开关门
* `IN-<personId>-<floor>-<elevatorId>`：乘客进入电梯
* `OUT-S-<personId>-<floor>-<elevatorId>`：乘客在**目的地**下电梯（S=success）
* `OUT-F-<personId>-<floor>-<elevatorId>`：乘客被**临时调度强制清空**导致未到目的地（F=fail/forced），会被重新投递回全局队列等待再分配
* `SCHE-BEGIN-<elevatorId>` / `SCHE-END-<elevatorId>`：临时调度任务开始/结束

## 核心设计与调度策略

### 1) 全局调度（DispatchThread）

* 从 `PassengerQueue.poll()` 阻塞取乘客
* 采用 **轮询（round-robin）** 将乘客分配给 1..6 号电梯
* 若目标电梯处于 **silent**（表示正在/待处理临时调度），则跳过并继续轮询
* 连续发现 6 部电梯都 silent 时，会 `sleep(6000)` 再继续尝试

> 分配成功时会打印 `RECEIVE-personId-elevatorId`，并将乘客放入该电梯的 `ElevatorRequestQueue`

### 2) 单电梯任务选择（ElevatorRequestQueue.startNewTask）

当电梯空闲需要选择下一任务时：

1. 若存在 `ScheRequest`（临时调度），**优先执行**（FIFO）
2. 否则在乘客请求中择优挑选“首个乘客”：

   * 统计乘客平均优先级 `avgPriority`
   * 若存在“**同方向且电梯当前方向上可达**”的乘客中，最高优先级 `maxPriority` 满足
     `maxPriority >= 1.5 * avgPriority`，则优先选该高优先级乘客
   * 否则优先选“同方向最近的乘客”，再否则选“反方向最近的乘客”

### 3) 开门与上/下客（ElevatorThread）

* 电梯维护一个 `passengers` 映射：`目标楼层 -> 乘客列表`，用于到站批量下客
* 开门条件 `isOpen()`：

  * 当前楼层存在可上车乘客（与当前方向一致），或
  * 当前楼层存在需要下车乘客
* 上客策略：

  * 在容量允许下，反复从队列挑选“当前楼层、方向一致”的乘客
  * 同一楼层同方向时优先级更高者优先上
  * 上客会动态延展 `targetFloor`（同方向更远的目的地会更新目标）

### 4) 临时调度（ScheRequest）

临时调度执行逻辑（`executeTempTask()`）：

1. 输出 `SCHE-BEGIN-id`
2. 按 `ScheRequest.getSpeed()` 速度移动到 `toFloor`
3. 开门并等待 1000ms
4. **清空电梯内所有乘客**：

   * 若乘客目的地就是当前层：输出 `OUT-S`
   * 否则输出 `OUT-F`，并将该乘客的 `fromFloor` 更新为当前层，然后重新放回全局 `PassengerQueue`（等待重新调度）
5. 输出 `SCHE-END-id`
6. 将该电梯队列里尚未处理的乘客请求也“退回”全局队列（避免 silent 期间积压）
7. 解除 silent（`outOfSilent()`）

## 结束条件

* `InputThread` 读到 `null` 请求时：

  * 标记 `PassengerQueue.noMoreNew = true`
* `DispatchThread` 检测：

  * 输入结束且所有电梯队列都“不再有待回收的临时调度影响”（`noMoreReturn()`）后，置全局 `end`
  * 全局队列为空且 end 为真时：

    * 对所有电梯队列 `setEnd()`
    * 调度线程结束
* `ElevatorThread` 在其队列 `isEmpty() && isEnd()` 时退出

## 本地回放测试（TestMain）

`TestMain` 会把标准输入替换为一个“按时间戳延迟喂入”的输入流，支持每行形如：

```
[<time_in_seconds>]实际请求内容
```

例如（示意）：

```
[0.0]...第一条请求
[1.5]...第二条请求
```

它会根据方括号内的秒数延迟注入该行内容，便于复现时序相关 bug。

运行方式：

```bash
java -cp .:path/to/oocourse-elevator3.jar TestMain
```
