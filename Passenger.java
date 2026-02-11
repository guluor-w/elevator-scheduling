import com.oocourse.elevator3.PersonRequest;
import com.oocourse.elevator3.Request;

public class Passenger extends Request {
    private PersonRequest personInformation;
    private String fromFloor;

    public Passenger(PersonRequest personInformation) {
        this.personInformation = personInformation;
        this.fromFloor = personInformation.getFromFloor();
    }

    public String getFromFloor() {
        return this.fromFloor;
    }

    public String getToFloor() {
        return this.personInformation.getToFloor();
    }

    public int getPersonId() {
        return this.personInformation.getPersonId();
    }

    public int getPriority() {
        return this.personInformation.getPriority();
    }

    public void setFromFloor(String fromFloor) {
        this.fromFloor = fromFloor;
    }
}
