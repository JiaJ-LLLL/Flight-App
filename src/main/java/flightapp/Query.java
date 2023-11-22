package flightapp;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Runs queries against a back-end database
 */
public class Query extends QueryAbstract {
  //
  // Canned queries
  //
  private static final String FLIGHT_CAPACITY_SQL = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement flightCapacityStmt;

  // queries for delete all tuples in reservation and user tables
  private static final String CLEAR_RESERVATIONS_SQL = "DELETE FROM Reservations_jl362";
  private PreparedStatement clearReservation;
  private static final String CLEAR_USER_SQL = "DELETE FROM Users_jl362";
  private PreparedStatement clearUsers;

  // queries for create a new user
  private static final String CREATE_USER_SQL = "INSERT INTO Users_jl362 VALUES (?, ?, ?)";
  private PreparedStatement createUser;
  private  static  final String FIND_USER_SQL = "SELECT * FROM Users_jl362 WHERE username = ?";
  private PreparedStatement findUser;

  // queries for finding all direct flight
  private static final String SEARCH_DIRECT_SQL = "SELECT TOP (?) f.fid, f.day_of_month, f.carrier_id, f.flight_num, f.origin_city, f.dest_city, f.actual_time, f.capacity, f.price \n"+
          "FROM Flights f WHERE f.origin_city = ? AND f.dest_city = ? AND f.day_of_month = ? AND f.canceled = 0 \n" +
          "ORDER BY f.actual_time, f.fid ASC";
  private PreparedStatement searchDirect;

  // query for finding the information of a flight
  private static final String FIND_FLIGHT_SQL = "SELECT day_of_month, carrier_id, flight_num, origin_city, dest_city, actual_time, capacity, price \n" +
          "FROM Flights f \n" +
          "WHERE f.fid = ?";
  private PreparedStatement findFlight;
  private static final String SEARCH_INDIRECT_SQL = "SELECT DISTINCT TOP(?) f1.fid AS fid1, f2.fid AS fid2, f1.actual_time + f2.actual_time AS total_time " +
          "FROM FLIGHTS f1, FLIGHTS f2 " +
          "WHERE f1.origin_city = ? AND " +
              "f1.dest_city = f2.origin_city AND " +
              "f2.dest_city = ? AND " +
              "f1.day_of_month = ? AND " +
              "f1.day_of_month = f2.day_of_month AND " +
              "f1.canceled = 0 AND " +
              "f2.canceled = 0 " +
  "ORDER BY f1.actual_time + f2.actual_time, f1.fid, f2.fid";
  private PreparedStatement searchIndirect;


  // query used for book
  private static final String CHECK_RESERVATION_DATE_SQL = "SELECT * FROM Reservations_jl362  AS r WITH (UPDLOCK) " +
                                                           "WHERE r.user_name = ? AND r.date = ?";
  private PreparedStatement searchDate;

  private  static final String CHECK_RESERVATION_CAPACITY_SQL = "SELECT COUNT(*) FROM Reservations_jl362 AS r WITH (UPDLOCK) " +
          "WHERE r.flight1 = ? or r.flight2 = ?";
  private PreparedStatement reservationCapacity;

  private static final String GET_NEXT_RID_SQL = "SELECT MAX(r.rid) FROM Reservations_jl362 AS r WITH (UPDLOCK) ";
  private PreparedStatement getRID;

  private static final String CREATE_RESERVATION_SQL =
          "INSERT INTO Reservations_jl362 VALUES (?, ?, ?, ?, ?, ?, ?);";
  private PreparedStatement createReservation;

  // query used for pay
  private static final String FIND_RESERVATION_SQL = "SELECT * FROM Reservations_jl362 AS r " +
          "WHERE r.rid = ?";
  private PreparedStatement findReservation;

  private static final String CHECK_BALANCE_SQL = "SELECT u.balance FROM Users_jl362 AS u WHERE u.username = ?";
  private PreparedStatement checkBalance;

  private static final String UPDATE_BALANCE_SQL = "UPDATE Users_jl362 SET balance = ? WHERE username = ?";
  private PreparedStatement updateBalance;

  private static final String UPDATE_RESERVATION_SQL = "UPDATE Reservations_jl362 SET paid = 1 WHERE rid = ?";
  private PreparedStatement updateReservation;

  // query used for reservation
  private static final String FIND_USER_RESERVATIONS_SQL = "SELECT * FROM Reservations_jl362 AS r WHERE r.user_name = ? ORDER BY r.rid ASC";
  private PreparedStatement findUserReservations;





  //
  // Instance variables
  //
  private String currUser = "";
  private List<Itinerary> resultItineraries;
  protected Query() throws SQLException, IOException {
    resultItineraries = new ArrayList<>();
    prepareStatements();
  }

  /**
   * Clear the data in any custom tables created.
   *
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      clearReservation.executeUpdate();
      clearUsers.executeUpdate();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    flightCapacityStmt = conn.prepareStatement(FLIGHT_CAPACITY_SQL);
    // clear reservation and user tables.
    clearReservation = conn.prepareStatement(CLEAR_RESERVATIONS_SQL);
    clearUsers = conn.prepareStatement(CLEAR_USER_SQL);
    // create a new user
    createUser = conn.prepareStatement(CREATE_USER_SQL);
    // find a user
    findUser = conn.prepareStatement(FIND_USER_SQL);
    // find direct flight
    searchDirect = conn.prepareStatement(SEARCH_DIRECT_SQL);
    // find indirect flight
    searchIndirect = conn.prepareStatement(SEARCH_INDIRECT_SQL);
    // find a given flight
    findFlight = conn.prepareStatement(FIND_FLIGHT_SQL);
    // get the current max RID
    getRID = conn.prepareStatement(GET_NEXT_RID_SQL);


    // find the date of a flight
    searchDate = conn.prepareStatement(CHECK_RESERVATION_DATE_SQL);
    // find the number of reservation of a flight
    reservationCapacity = conn.prepareStatement(CHECK_RESERVATION_CAPACITY_SQL);
    // create new reservation
    createReservation = conn.prepareStatement(CREATE_RESERVATION_SQL);

    // find the reservation
    findReservation = conn.prepareStatement(FIND_RESERVATION_SQL);
    // check the balance of the current user
    checkBalance = conn.prepareStatement(CHECK_BALANCE_SQL);
    // update the balance
    updateBalance = conn.prepareStatement(UPDATE_BALANCE_SQL);
    // update the reservation
    updateReservation = conn.prepareStatement(UPDATE_RESERVATION_SQL);


    // find all the reservation of a user
    findUserReservations = conn.prepareStatement(FIND_USER_RESERVATIONS_SQL);

  }


  /* See QueryAbstract.java for javadoc */
  public String transaction_login(String username, String password) {
    String username_lowercase = username.toLowerCase();
    if (!currUser.equals("")) {
      return "User already logged in\n";
    }
    try {
      findUser.clearParameters();
      findUser.setString(1, username_lowercase);
      ResultSet tuple = findUser.executeQuery();
      if (tuple.next()) {
        byte[] savedPassword = tuple.getBytes(2);
        if (PasswordUtils.plaintextMatchesSaltedHash(password, savedPassword)) {
          currUser = username_lowercase;
          return "Logged in as " + username + "\n";
        }
      }
      return "Login failed\n";
    } catch (SQLException e) {
      return "Login failed\n";
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    // TODO: YOUR CODE HERE
    if (initAmount < 0) {
      return "Failed to create user\n";
    }
    // check for the existence of the user
    String username_lowercase = username.toLowerCase();
    try {
      createUser.clearParameters();
      createUser.setString(1, username_lowercase);
      createUser.setBytes(2, PasswordUtils.saltAndHashPassword(password));
      createUser.setInt(3, initAmount);
      createUser.executeUpdate();
      return "Created user " + username +  "\n";
    } catch (SQLException e) {
      return "Failed to create user\n";
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_search(String originCity, String destinationCity, 
                                   boolean directFlight, int dayOfMonth,
                                   int numberOfItineraries) {
    // WARNING: the below code is insecure (it's susceptible to SQL injection attacks) AND only
    // handles searches for direct flights.  We are providing it *only* as an example of how
    // to use JDBC; you are required to replace it with your own secure implementation.
    //
    // TODO: YOUR CODE HERE
    StringBuffer sb = new StringBuffer();
    try {
      searchDirect.clearParameters();
      searchDirect.setInt(1, numberOfItineraries);
      searchDirect.setString(2, originCity);
      searchDirect.setString(3, destinationCity);
      searchDirect.setInt(4, dayOfMonth);
      ResultSet oneHopResults = searchDirect.executeQuery();
      while (oneHopResults.next()) {
        int result_fid = oneHopResults.getInt("fid");
        int result_dayOfMonth = oneHopResults.getInt("day_of_month");
        String result_carrierId = oneHopResults.getString("carrier_id");
        String result_flightNum = oneHopResults.getString("flight_num");
        String result_originCity = oneHopResults.getString("origin_city");
        String result_destCity = oneHopResults.getString("dest_city");
        int result_time = oneHopResults.getInt("actual_time");
        int result_capacity = oneHopResults.getInt("capacity");
        int result_price = oneHopResults.getInt("price");
        Flight f = new Flight(result_fid, result_dayOfMonth, result_carrierId,
                              result_flightNum, result_originCity, result_destCity, result_time,
                              result_capacity, result_price);
        Itinerary i = new Itinerary(-1, result_dayOfMonth, f, null, result_originCity, result_destCity);
        resultItineraries.add(i);
      }
      oneHopResults.close();
      int numberOfLeft = numberOfItineraries - resultItineraries.size();
      if (!directFlight && numberOfLeft > 0) {
        searchIndirect.clearParameters();
        searchIndirect.setInt(1, numberOfLeft);
        searchIndirect.setString(2, originCity);
        searchIndirect.setString(3, destinationCity);
        searchIndirect.setInt(4, dayOfMonth);
        ResultSet twoHopResults = searchIndirect.executeQuery();
        while (twoHopResults.next()) {
          int result_fid1 = twoHopResults.getInt("fid1");
          int result_fid2 = twoHopResults.getInt("fid2");
          Flight flight1 = getFlight(result_fid1);
          Flight flight2 = getFlight(result_fid2);
          Itinerary iti = new Itinerary(-1, flight1.dayOfMonth, flight1, flight2, originCity, destinationCity);
          resultItineraries.add(iti);
        }
        twoHopResults.close();
      }
      resultItineraries.sort(new ItineraryComparator());
      if (resultItineraries.isEmpty()) {
        return "No flights match your selection\n";
      }
      int id = 0;
      for (Itinerary i: resultItineraries) {
        i.fid = id;
        sb.append(i.toString());
        sb.append("\n");
        id++;
      }
    } catch (SQLException e) {
      e.printStackTrace();
      return "Failed to search\n";
    }
    return sb.toString();
  }

  /**
   * Helper comparator used for compare two itineraries based on total_time.
   * If total_time is the same, break the tie using fid.
   */
  public class ItineraryComparator implements Comparator<Itinerary> {
    @Override
    public int compare(Itinerary it1, Itinerary it2) {
      // First compare the total_time
      int timeCompare = Integer.compare(it1.total_time, it2.total_time);
      if (timeCompare != 0) {
        // If total_time is not the same, return the comparison result
        return timeCompare;
      } else {
        // If total_time is the same, break the tie using fid
        return Integer.compare(it1.fid, it2.fid);
      }
    }
  }

  /**
   * Helper function used for finding all information about a flight based on input fid
   * @param fid the fid of the flight we are searching for
   * @return A new flight with given fid.
   */
  private Flight getFlight(int fid) {
    try {
      findFlight.clearParameters();
      findFlight.setInt(1, fid);
      ResultSet flightInfo =  findFlight.executeQuery();
      flightInfo.next();
      int result_dayOfMonth = flightInfo.getInt("day_of_month");
      String result_carrierId = flightInfo.getString("carrier_id");
      String result_flightNum = flightInfo.getString("flight_num");
      String result_originCity = flightInfo.getString("origin_city");
      String result_destCity = flightInfo.getString("dest_city");
      int result_time = flightInfo.getInt("actual_time");
      int result_capacity = flightInfo.getInt("capacity");
      int result_price = flightInfo.getInt("price");
      Flight f = new Flight(fid, result_dayOfMonth, result_carrierId,
              result_flightNum, result_originCity, result_destCity, result_time,
              result_capacity, result_price);
      return f;
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return null;
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_book(int itineraryId) {
    // TODO: YOUR CODE HERE
    // check if the user already login.
    if (currUser.equals("")) {
      return "Cannot book reservations, not logged in\n";
    }
    // check if the user already done a search and the id is vaild
    if (itineraryId >= resultItineraries.size()) {
      return "No such itinerary " + itineraryId + "\n";
    }
    try {
      conn.setAutoCommit(false);
      try {
        Itinerary itinerary = resultItineraries.get(itineraryId);
        Flight flight_one = itinerary.flight_one;
        Flight flight_two = itinerary.flight_second;
        searchDate.clearParameters();
        searchDate.setString(1, currUser);
        searchDate.setInt(2, itinerary.day_of_month);
        ResultSet resultReservation = searchDate.executeQuery();
        if (resultReservation.isBeforeFirst()) {
          conn.commit();
          conn.setAutoCommit(true);
          return "You cannot book two flights in the same day\n";
        }

        // check the capacity of the flight
        int flight_one_reservation = CountReservation(flight_one.fid);
        if (flight_one_reservation < flight_one.capacity) {
          if (flight_two != null) {
            int flight_two_reservation = CountReservation(flight_two.fid);
            if (flight_two_reservation >= flight_two.capacity) {
              conn.commit();
              conn.setAutoCommit(true);
              return "Booking failed\n";
            }
          }
        } else {
          conn.commit();
          conn.setAutoCommit(true);
          return "Booking failed\n";
        }
        // create new reservation and insert it into database
        // get the next reservation id: rid
        int rid = GetRID();
        int totalPrice = flight_one.price;
        createReservation.clearParameters();
        createReservation.setInt(1, rid);
        createReservation.setInt(2, 0);
        createReservation.setInt(3, flight_one.fid);
        if (flight_two != null) {
          totalPrice += flight_two.price;
          createReservation.setInt(4, flight_two.fid);
        } else {
          createReservation.setNull(4, 4);
        }
        createReservation.setString(5, currUser);
        createReservation.setInt(6, itinerary.day_of_month);
        createReservation.setInt(7, totalPrice);
        createReservation.executeUpdate();

        conn.commit();
        conn.setAutoCommit(true);
        return "Booked flight(s), reservation ID: " + rid + "\n";
      } catch (SQLException e) {
        conn.rollback();
        conn.setAutoCommit(true);
        e.printStackTrace();
        return "Booking failed\n";
      }
    } catch (Exception e) {
      e.printStackTrace();
      return "Booking failed\n";
    }
  }

  private int CountReservation(int fid) throws SQLException {
    reservationCapacity.clearParameters();
    reservationCapacity.setInt(1, fid);
    reservationCapacity.setInt(2, fid);
    ResultSet resultCapacity = reservationCapacity.executeQuery();
    resultCapacity.next();
    return resultCapacity.getInt(1);
  }

  private int GetRID() throws SQLException {
    ResultSet r = getRID.executeQuery();
    if (!r.isBeforeFirst()) {
      return 1;
    }
    r.next();
    return r.getInt(1) + 1;
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_pay(int reservationId) {
    if (currUser.equals("")) {
      return "Cannot pay, not logged in\n";
    }
    try {
      conn.setAutoCommit(false);
      try {
        // try to get reservation
        findReservation.clearParameters();
        findReservation.setInt(1, reservationId);
        ResultSet reservation =  findReservation.executeQuery();
        // check the existence of the reservation
        if (!reservation.isBeforeFirst()) {
          conn.commit();
          conn.setAutoCommit(true);
          return "Cannot find unpaid reservation " + reservationId + " under user: " + currUser + "\n";
        }
        reservation.next();
        boolean isPaid = reservation.getInt(2) == 1;
        String username = reservation.getString(5);
        // check if paid and does the usernames match
        if (isPaid || !username.equals(currUser)) {
          conn.commit();
          conn.setAutoCommit(true);
          return "Cannot find unpaid reservation " + reservationId + " under user: " + currUser + "\n";
        }
        checkBalance.clearParameters();
        checkBalance.setString(1, currUser);
        ResultSet resultBalance = checkBalance.executeQuery();
        resultBalance.next();
        int balance = resultBalance.getInt(1);
        checkBalance.close();
        int price = reservation.getInt(7);
        if (balance < price) {
          conn.commit();
          conn.setAutoCommit(true);
          return "User has only "+ balance + " in account but itinerary costs "+ price +"\n";
        }

        balance = balance - price;
        updateBalance.clearParameters();
        updateBalance.setInt(1, balance);
        updateBalance.setString(2, currUser);
        updateBalance.executeUpdate();

        updateReservation.clearParameters();
        updateReservation.setInt(1, reservationId);
        updateReservation.executeUpdate();
        conn.commit();
        conn.setAutoCommit(true);
        conn.commit();
        conn.setAutoCommit(true);
        return "Paid reservation: " + reservationId + " remaining balance: " + balance + "\n";
      } catch (SQLException e) {
        conn.rollback();
        conn.setAutoCommit(true);
        e.printStackTrace();
        return "Failed to pay for reservation " + reservationId + "\n";
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_reservations() {
    // TODO: YOUR CODE HERE
    if (currUser.equals("")) {
      return "Cannot view reservations, not logged in\n";
    }
    try {
      findUserReservations.clearParameters();
      findUserReservations.setString(1, currUser);
      ResultSet reservation = findUserReservations.executeQuery();
      if (!reservation.isBeforeFirst()) {
        return "No reservations found\n";
      }
      StringBuffer sb = new StringBuffer();
      while (reservation.next()) {
        int rid = reservation.getInt(1);
        boolean paid = reservation.getInt(2) == 1;
        int fid1 = reservation.getInt(3);
        Flight flight1 = getFlight(fid1);
        sb.append("Reservation " + rid + " paid: " + paid + ":\n");
        sb.append(flight1.toString() + "\n");
        int fid2 = reservation.getInt(4);
        if (fid2 != 0){
          Flight flight2 = getFlight(fid2);
          sb.append(flight2.toString() + "\n");
        }
      }
      return sb.toString();
    } catch (SQLException e) {
      e.printStackTrace();
      return "Failed to retrieve reservations\n";
    }
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    flightCapacityStmt.clearParameters();
    flightCapacityStmt.setInt(1, fid);

    ResultSet results = flightCapacityStmt.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  /**
   * Utility function to determine whether an error was caused by a deadlock
   */
  private static boolean isDeadlock(SQLException e) {
    return e.getErrorCode() == 1205;
  }

  /**
   * A class to store information about a single flight
   *
   * TODO(hctang): move this into QueryAbstract
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    Flight(int id, int day, String carrier, String fnum, String origin, String dest, int tm,
           int cap, int pri) {
      fid = id;
      dayOfMonth = day;
      carrierId = carrier;
      flightNum = fnum;
      originCity = origin;
      destCity = dest;
      time = tm;
      capacity = cap;
      price = pri;
    }
    
    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
          + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
          + " Capacity: " + capacity + " Price: " + price;
    }
  }

  /**
   * A helper class that stores the information about an itinerary.
   */
  class Itinerary {
    public int fid;
    public int day_of_month;
    public Flight flight_one;
    public Flight flight_second;
    public String origin_city;
    public String dest_city;
    public int total_time;
    public Itinerary(int fid, int day_of_month, Flight flight_one, Flight flight_second, String origin_city,
                     String dest_city) {
      this.fid = fid;
      this.day_of_month = day_of_month;
      this.flight_one = flight_one;
      this.flight_second = flight_second;
      this.origin_city = origin_city;
      this.dest_city = dest_city;
      if (flight_second == null) {
        this.total_time = flight_one.time;
      } else {
        this.total_time = flight_one.time + flight_second.time;
      }
    }

    @Override
    public String toString() {
      String result = "Itinerary " + fid + ": ";
      if (flight_second == null) {
        result += "1 flight(s), " + total_time + " minutes\n";
        result += flight_one.toString();
      } else {
        result += "2 flight(s), " + total_time + " minutes\n";
        result += flight_one.toString() + "\n";
        result += flight_second.toString();
      }
      return result;
    }

  }
}
