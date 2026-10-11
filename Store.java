package ai;

import java.util.List;

/** Storage abstraction: MySQL when available, otherwise a local file so the app always works. */
interface Store {
    String name();
    User register(String username, String phone, String password) throws StoreException;
    User login(String username, String password) throws StoreException;
    List<Vehicle> vehicles(int userId) throws StoreException;
    Vehicle saveVehicle(Vehicle v) throws StoreException;
    void deleteVehicle(int vehicleId, int userId) throws StoreException;
    Report saveReport(Report r) throws StoreException;
    List<Report> reports(int userId) throws StoreException;
}
