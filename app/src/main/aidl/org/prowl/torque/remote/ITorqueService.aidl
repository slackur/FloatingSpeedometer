package org.prowl.torque.remote;

interface ITorqueService {
    boolean isConnectedToECU();
    String[] listAllPIDs();
    float[] getPIDValues(in String[] pids);
    String getPIDInformation(String pid);
}
