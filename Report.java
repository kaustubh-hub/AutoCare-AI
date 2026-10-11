package ai;

import java.io.Serializable;
import java.time.LocalDate;

class Report implements Serializable, Cloneable {
    private static final long serialVersionUID = 1L;
    int id, userId, vehicleId, mileage, healthScore, pointsEarned;
    String invoiceNo = "", vehicleName = "", registration = "", customer = "", issue = "", advisor = "";
    LocalDate createdOn, nextServiceDue;
    double diagnosticFee, partsCost, subtotal, gst, total;
    String diagnosisText = "", invoiceText = "", notes = "";
    Report copy() { try { return (Report) clone(); } catch (CloneNotSupportedException e) { throw new IllegalStateException(e); } }
}
