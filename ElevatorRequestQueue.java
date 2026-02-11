import com.oocourse.elevator3.Request;
import com.oocourse.elevator3.ScheRequest;

import java.util.ArrayList;

public class ElevatorRequestQueue {
    private final ArrayList<Passenger> elevatorPassengers = new ArrayList<>();
    private final ArrayList<ScheRequest> scheRequests = new ArrayList<>();
    private volatile boolean isEnd = false;
    private volatile boolean isSilent = false;

    public synchronized void offer(Passenger passenger) {
        elevatorPassengers.add(passenger);
        this.notifyAll();
    }

    public Passenger pickPassenger(int floor, int direction) {
        String floorStr = Trans.trans1(floor);
        Passenger bestPassenger = null;
        int maxPriority = 0;
        synchronized (this) {
            for (Passenger passenger : elevatorPassengers) {
                if (passenger.getFromFloor().equals(floorStr) &&
                    direct(passenger.getFromFloor(), passenger.getToFloor()) == direction) {
                    if (passenger.getPriority() > maxPriority) {
                        maxPriority = passenger.getPriority();
                        bestPassenger = passenger;
                    }
                }
            }
            if (bestPassenger != null) { elevatorPassengers.remove(bestPassenger); }
        }
        return bestPassenger;
    }

    public boolean isOpen(int floor, int direction) {
        String floorStr = Trans.trans1(floor);
        synchronized (this) {
            for (Passenger passenger : elevatorPassengers) {
                if (passenger.getFromFloor().equals(floorStr) &&
                    direct(passenger.getFromFloor(), passenger.getToFloor()) == direction) {
                    return true;
                }
            }
        }
        return false;
    }

    public Request startNewTask(int floor, int direction) {
        String floorStr = Trans.trans1(floor);
        long sumPriority = 0;
        Passenger maxPriorityPassenger = null;
        Passenger nearestSameDirectionPassenger = null;
        Passenger nearestOppositeDirectionPassenger = null;
        int maxPriority = 0;
        int minSameDirectionDistance = Integer.MAX_VALUE;
        int minOppositeDirectionDistance = Integer.MAX_VALUE;
        synchronized (this) {
            while (elevatorPassengers.isEmpty() && scheRequests.isEmpty() && !isEnd) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            if (!scheRequests.isEmpty()) {
                return scheRequests.remove(0);
            }
            else if (elevatorPassengers.isEmpty()) {
                return null;
            }
            for (Passenger passenger : elevatorPassengers) {
                sumPriority += passenger.getPriority();
                // 策略1：记录最高优先级请求（仅当方向一致时）
                if (passenger.getPriority() > maxPriority
                    && direct(floorStr, passenger.getFromFloor()) == direction) {
                    maxPriority = passenger.getPriority();
                    maxPriorityPassenger = passenger;
                }
                // 策略2：记录最近距离请求（区分同方向和反方向）
                int distance = Math.abs(floor - Trans.trans2(passenger.getFromFloor()));
                boolean isSameDirection = direct(floorStr, passenger.getFromFloor()) == direction;
                boolean isTerminalDirectionValid =
                    direct(passenger.getFromFloor(), passenger.getToFloor()) == direction;

                if (isSameDirection && isTerminalDirectionValid) {
                    if (distance < minSameDirectionDistance) {
                        minSameDirectionDistance = distance;
                        nearestSameDirectionPassenger = passenger;
                    }
                } else {
                    if (distance < minOppositeDirectionDistance) {
                        minOppositeDirectionDistance = distance;
                        nearestOppositeDirectionPassenger = passenger;
                    }
                }
            }
        }
        double avgPriority = (double) sumPriority / elevatorPassengers.size();
        boolean shouldPrioritize = (maxPriority >= 1.5 * avgPriority)
            && (maxPriorityPassenger != null);

        return shouldPrioritize ? maxPriorityPassenger
                : (nearestSameDirectionPassenger != null) ? nearestSameDirectionPassenger
                : nearestOppositeDirectionPassenger;
    }

    public synchronized void setEnd() {
        isEnd = true;
        notifyAll();
    }

    public synchronized boolean isEnd() {
        return isEnd;
    }

    public synchronized boolean isEmpty() {
        return elevatorPassengers.isEmpty() && scheRequests.isEmpty();
    }

    private int direct(String fromFloorStr, String toFloorStr) {
        int fromFloor = Trans.trans2(fromFloorStr);
        int toFloor = Trans.trans2(toFloorStr);
        if (fromFloor > toFloor) {
            return -1;
        }
        else if (fromFloor < toFloor) {
            return 1;
        }
        return 0;
    }
    //-------------------------------临时调度------------------------//

    public synchronized void haveTempTask(ScheRequest scheRequest) {
        scheRequests.add(scheRequest);
        this.isSilent = true;
        this.notifyAll();
    }

    public synchronized ScheRequest getScheRequest() {
        if (scheRequests.isEmpty()) { return null; }
        else {
            return scheRequests.remove(0);
        }
    }

    public synchronized boolean noMoreReturn() {
        return scheRequests.isEmpty() && !isSilent;
    }

    public synchronized boolean isSilent() {
        return isSilent;
    }

    public synchronized void outOfSilent() {
        this.isSilent = false;
    }

    public synchronized Passenger returnP() {
        if (elevatorPassengers.isEmpty())  { return null; }
        return elevatorPassengers.remove(0);
    }

}
