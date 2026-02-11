import com.oocourse.elevator3.Request;
import com.oocourse.elevator3.ScheRequest;
import com.oocourse.elevator3.TimableOutput;

import java.util.ArrayList;
import java.util.HashMap;

public class ElevatorThread extends Thread {
    public static final int MAX_CAPACITY = 6;
    private final ElevatorRequestQueue elevatorRequestQueue;
    private final PassengerQueue passengerQueue;
    private final HashMap<Integer, ArrayList<Passenger>> passengers;
    private final int id;            //1-6
    private int currentFloor;  //当前所在楼层
    private int targetFloor;   //去往的楼层
    private int remainedPlace;    //当前电梯剩余位置
    private int direction;     //方向 1向上，-1向下，0悬停
    private int state; //记录电梯状态    1刚到达  2开门进人  3关门运行  0任务完成（未开始）关门悬停  -1结束工作   4临时调度
    private final ArrayList<ScheRequest> scheRequests = new ArrayList<>();

    public ElevatorThread(int id,
        ElevatorRequestQueue elevatorRequestQueue,PassengerQueue passengerQueue) {
        this.elevatorRequestQueue = elevatorRequestQueue;
        this.passengerQueue = passengerQueue;
        this.passengers = new HashMap<>();
        this.id = id;
        this.currentFloor = 0;
        this.targetFloor = currentFloor;
        this.direction = 0;
        this.remainedPlace = MAX_CAPACITY;
        this.state = 0;
    }

    private int toStart() throws InterruptedException {
        Request newRequest = null;
        Passenger firstPassenger = null;
        int nextState = 0;
        while (true) {
            synchronized (elevatorRequestQueue) {
                if (elevatorRequestQueue.isEmpty() && elevatorRequestQueue.isEnd()) {   //TODO
                    nextState = -1;
                    break;
                }
                newRequest = elevatorRequestQueue.startNewTask(currentFloor,direction);
            }

            if (newRequest == null) {
                continue;
            }
            if (newRequest instanceof ScheRequest) {
                scheRequests.add((ScheRequest) newRequest);
                nextState = 4;
                break;
            }
            else if (newRequest instanceof Passenger) {
                firstPassenger = (Passenger) newRequest;
                nextState = 1;
                int startFloor = Trans.trans2(firstPassenger.getFromFloor());
                int distance = Math.abs(startFloor - currentFloor);
                direction = Integer.compare(startFloor, currentFloor);
                for (int i = 1; i <= distance; i++) {
                    this.move(1, 0.4);
                    ScheRequest newScheRequest = null;
                    if ((newScheRequest = elevatorRequestQueue.getScheRequest()) != null) {
                        scheRequests.add(newScheRequest);
                        nextState = 4;
                        break;
                    }
                }
                if (nextState == 1) {
                    targetFloor = Trans.trans2(firstPassenger.getToFloor());
                    direction = Integer.compare(targetFloor, currentFloor);
                }
                break;
            }
        }
        return nextState;
    }

    private boolean isOpen() {
        return (elevatorRequestQueue.isOpen(currentFloor, direction)) ||
                passengers.containsKey(currentFloor);
    }

    private void out(int type) {
        if (passengers == null) {
            return;
        }

        ArrayList<Passenger> leftGroup = new ArrayList<>();
        if (type == 1) {
            if (!passengers.containsKey(currentFloor)) { return; }
            leftGroup = passengers.get(currentFloor);
            passengers.remove(currentFloor);
        }
        else if (type == 2) {
            for (HashMap.Entry<Integer, ArrayList<Passenger>> entry : passengers.entrySet()) {
                leftGroup.addAll(entry.getValue());
            }
            passengers.clear();
        }
        int leftNum = leftGroup.size();
        remainedPlace += leftNum;
        String currentFloorStr = Trans.trans1(currentFloor);
        for (int i = 0; i < leftNum; i++) {
            Passenger passenger = leftGroup.get(i);
            if (type == 1 || currentFloor == Trans.trans2(passenger.getToFloor())) {
                TimableOutput.println("OUT-S-" + passenger.getPersonId() + "-"
                    + Trans.trans1(currentFloor)  + "-" + id);
            }
            else {
                TimableOutput.println("OUT-F-" + passenger.getPersonId() + "-"
                    + Trans.trans1(currentFloor)  + "-" + id);
                passenger.setFromFloor(currentFloorStr);
                passengerQueue.offer(passenger);
            }
        }
    }

    private void arrive() {
        TimableOutput.println("ARRIVE-" + Trans.trans1(currentFloor)  + "-" + id);
    }

    private void open() {
        TimableOutput.println("OPEN-" + Trans.trans1(currentFloor)  + "-" + id);
    }

    private void in() {
        while (remainedPlace > 0) {
            Passenger newPassenger =
                elevatorRequestQueue.pickPassenger(currentFloor, direction);
            if (newPassenger == null) {
                break;
            }
            int tempTarget = Trans.trans2(newPassenger.getToFloor());
            if ((direction == 1 && tempTarget > targetFloor) ||
                (direction == -1 && tempTarget < targetFloor)) {
                targetFloor = tempTarget;
            }

            if (passengers.containsKey(tempTarget)) {
                passengers.get(tempTarget).add(newPassenger);
            } else {
                ArrayList<Passenger> newGroup = new ArrayList<>();
                newGroup.add(newPassenger);
                passengers.put(tempTarget, newGroup);
            }
            remainedPlace--;
            TimableOutput.println("IN-" + newPassenger.getPersonId() + "-"
                + Trans.trans1(currentFloor) + "-" + id);
        }
    }

    private void close() {
        TimableOutput.println("CLOSE-" + Trans.trans1(currentFloor)  + "-" + id);
    }

    private void move(int num, double speed) throws InterruptedException {
        for (int i = 1; i <= num; i++) {
            this.sleep((long) (speed * 1000));
            if (direction == -1) { currentFloor--; }
            else if (direction == 1) { currentFloor++; }
            arrive();
        }
    }

    private void executeTempTask() throws InterruptedException {
        ScheRequest scheRequest = scheRequests.remove(0);
        //elevatorRequestQueue.setSilent();
        TimableOutput.println("SCHE-BEGIN-" + id);
        targetFloor = Trans.trans2(scheRequest.getToFloor());
        direction = Integer.compare(targetFloor, currentFloor);
        double speed = scheRequest.getSpeed();
        int distance = Math.abs(targetFloor - currentFloor);
        move(distance, speed);
        open();
        sleep(1000);
        out(2);
        close();
        TimableOutput.println("SCHE-END-" + id);
        Passenger returnPassenger;
        while ((returnPassenger = elevatorRequestQueue.returnP()) != null) {
            passengerQueue.offer(returnPassenger);
        }
        elevatorRequestQueue.outOfSilent();
        synchronized (passengerQueue) {
            passengerQueue.notifyAll();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                ScheRequest scheRequest = null;
                //System.out.println(id +"号电梯当前的state：" + state);
                switch (state) {
                    case 0:  //任务完成或者未开始
                        //System.out.println(id+"号电梯悬停");
                        state = toStart();
                        break;
                    case 1:   //刚到达某一层
                        if (isOpen()) {
                            state = 2;
                        } else if ((scheRequest = elevatorRequestQueue.getScheRequest()) != null) {
                            scheRequests.add(scheRequest);
                            state = 4;
                        } else {
                            state = 3;
                        }
                        break;
                    case 2: //开门进人关门
                        open();
                        sleep(400);
                        out(1);
                        in();
                        close();
                        if ((scheRequest = elevatorRequestQueue.getScheRequest()) != null) {
                            scheRequests.add(scheRequest);
                            state = 4;
                        } else if (targetFloor == currentFloor) {
                            state = 0;
                        } else {
                            state = 3;
                        }
                        break;
                    case 3:  //移动
                        move(1,0.4);
                        state = 1;
                        break;
                    case 4: //临时调度
                        executeTempTask();
                        state = 0;
                        break;
                    default:
                        break;
                }
                if (state == -1) {
                    //System.out.println("结束线程"+id);
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
