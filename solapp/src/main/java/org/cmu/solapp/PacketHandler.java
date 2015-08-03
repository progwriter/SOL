package org.cmu.solapp;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.opendaylight.controller.sal.action.Action;
import org.opendaylight.controller.sal.action.Output;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

public class PacketHandler {

    private static final Logger log = LoggerFactory.getLogger(PacketHandler.class);
    private static final byte MAXTHREADS = 8; //Number of worker threads to be employed. 
    static final String pathToJsonFile = "/tmp/flows.json"; //Path to the JSON file generated by SOL
    static final String pathToSolApp = "/home/dipayan/sol/examples"; //Path to the SOL app to be run
    static final String appToExecute = "SIMPLER.py"; //Name of the SOL app to be run
    /* For benchmarking*/
    long starttime;
    long endtime;
    
    /* For multithreading */
    ArrayList<Thread> tlist;
    BlockingQueue<InstallFlowThread> flowThreadList;
    int countFlows;
    int numFlows;

    private IFlowProgrammerService flowProgrammerService;
    private ISwitchManager switchManager;

    static private InetAddress convertMaskToInet(String addr) {
    	/* Function to convert subnet mask into InetAddress format.
    	 * :param addr : Subnet mask. For example, "8"
    	 */
        String[] parts = addr.split("/");
        //String ip = parts[0];
        int prefix;
        if (parts.length < 2) {
            prefix = 0;
        } else {
            prefix = Integer.parseInt(parts[1]);
        }
        int mask = 0xffffffff << (32 - prefix);
        //System.out.println("Prefix=" + prefix);
        //System.out.println("Address=" + ip);

        int value = mask;
        byte[] bytes = new byte[]{
                (byte) (value >>> 24), (byte) (value >> 16 & 0xff), (byte) (value >> 8 & 0xff), (byte) (value & 0xff)};
        InetAddress netAddr;
        try {
            netAddr = InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            System.out.println("Mask could not be converted to InetAddress");
            return null;
        }
        //System.out.println("Mask=" + netAddr.getHostAddress());
        return netAddr;
    }

    static private byte[] stringToByteMac(String mac) {
    	/*String to convert MAC address in string format to Byte Array format.
    	 * : param mac: MAC address in String format. Eg:- "00:00:00:00:00:01"
    	 */
        String[] parts = mac.split(Pattern.quote(":"));
        byte[] bytemac = new byte[6];
        for (int i = 0; i < parts.length; i++) {
            Integer hex = Integer.parseInt(parts[i], 16);
            bytemac[i] = hex.byteValue();
        }
        return bytemac;
    }

    private NodeConnector stringToNodeConnector(Node node, String portnum) {
    	/* Convert portnumber from String to NodeConnector object.
    	 * :param node: Node object.
    	 * :param portnum: Port number of the node object passed in String format.
    	 * Eg: "2"
    	 */
        String node_str = node.toString();
        String[] parts = node_str.split(Pattern.quote("|"));

        String s = portnum + "@OF";
        String nc_str = parts[0] + "|" + s + "|" + parts[1];

        return NodeConnector.fromString(nc_str);
    }

    static private InetAddress stringToInetAddress(String i) {
    	/* Function to convert IP address in String format to InetAddress object.
    	 * :param i: IP address in String format (without subnet mask).
    	 * Eg: "10.0.0.1"
    	 */
        InetAddress addr;
        String[] parts = i.split(Pattern.quote("/"));
        if (parts.length >= 2)
            i = parts[0];
        try {
            addr = InetAddress.getByName(i);
        } catch (UnknownHostException e) {
            return null;
        }
        return addr;
    }

    void setFlowProgrammerService(IFlowProgrammerService s) {
    	/* Sets the flow programmer service for usage in the app.
    	 * :param s : IFlowProgrammerService Object.
    	 */
        log.trace("Set FlowProgrammerService.");

        flowProgrammerService = s;
    }

    void unsetFlowProgrammerService(IFlowProgrammerService s) {
        log.trace("Removed FlowProgrammerService.");

        if (flowProgrammerService == s) {
            flowProgrammerService = null;
        }
    }

    void setSwitchManagerService(ISwitchManager s) {
        log.trace("Set SwitchManagerService.");

        switchManager = s;
    }

    void unsetSwitchManagerService(ISwitchManager s) {
        log.trace("Removed SwitchManagerService.");

        if (switchManager == s) {
            switchManager = null;
        }
    }

    void init() {
        log.trace("INIT called!");
    }

    void destroy() {
        log.trace("DESTROY called!");
    }

    void start() throws IOException, InterruptedException {
        log.trace("START called!");
        tlist = new ArrayList<Thread>();
        flowThreadList = new LinkedBlockingQueue<InstallFlowThread>();
        countFlows = 0;

        try {
        	//Calling the main function that installs all the flows.
            this.installFlows();
        } catch (JSONException e) {
            System.out.println("Caught JSON Exception!");
        }
    }

    void stop() {
        log.debug("STOP called!");
    }

    private void executeSolOptimization() throws IOException, InterruptedException {
    	/* This function is used to execute the SOL app which creates the JSON file required
    	 *  to install flows in ODL.
    	 */
        ProcessBuilder pb = new ProcessBuilder("python", appToExecute);
        pb.directory(new File(pathToSolApp));
        pb.redirectError();
        Process sol = pb.start();
        sol.waitFor(); //Waitng for SOL python  app to complete its job and create the JSON file. 
    }

    public JSONArray getFlowsFromSol() throws IOException, InterruptedException {
    	/* This function is used to parse the JSON file generated by SOL to generate a 
    	 *  JSONArray object. This function also deletes the JSON file created by the
    	 *  SOL python app. 
    	 */
        starttime = System.currentTimeMillis();
        executeSolOptimization();
        endtime = System.currentTimeMillis();
        System.out.println("\n\n\nExecuting sol = " + (endtime - starttime) + " msecs!\n\n\n");
        starttime = System.currentTimeMillis();

        File jsfile = new File(pathToJsonFile);
        if (!(jsfile.exists())) {
            System.out.println("JSON file could not be found!");
            return null;
        }
        Scanner in;
        try {
            in = new Scanner(new FileReader(pathToJsonFile));
        } catch (FileNotFoundException e) {
            System.out.println("File Not Found");
            return null;
        }
        String str = "";
        while (in.hasNextLine()) {
            str = str.concat(in.nextLine()) + "\n";
        }
        in.close();
        jsfile.delete();
        JSONArray arr;
        try {
            arr = new JSONArray(str);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
     // Storing the number of flows acquired from SOL in a global variable
        numFlows = arr.length(); 
       // System.out.println("Number of flows got from SOL = " + numFlows);
        return arr;
    }

    public void processFlowsFromSol(JSONArray arr) throws InterruptedException {
    	/* This function is used to parse the JSON Array generated by getFlowsFromSOL()
    	 *  into FlowsFromSOL object.
    	 */
        try {
            for (int i = 0; i < numFlows; i++) {
                JSONObject obj = arr.getJSONObject(i);
                FlowFromSol f = new FlowFromSol();
                f.flowName = obj.getString("flowName");
                f.outPort = obj.getString("outPort");
                f.ethDst = obj.getString("ethDst");
                f.etherType = obj.getString("etherType");
                f.ethSrc = obj.getString("ethSrc");
                f.nodeId = obj.getString("nodeId");
                f.inPort = obj.getString("inPort");
                f.installHw = obj.getString("installHw");
                f.priority = obj.getString("priority");
                f.flowId = obj.getString("flowId");
                f.srcIpPrefix = obj.getString("srcIpPrefix");
                f.dstIpPrefix = obj.getString("dstIpPrefix");
                InstallFlowThread flowThread = new InstallFlowThread(f);
                flowThreadList.put(flowThread);
            }
        } catch (JSONException e) {
            System.out.println("Caught JSON Exception!");
            return;
        }
    }

    public void installFlows() throws JSONException, IOException, InterruptedException {
    	
    	/* This is the main function of this JAVA app that is called by the start() funtion.
    	 *  This function creates the worker threads, calls all other functions that parse the flows
    	 *  to be installed into JAVA object and then, the worker threads install these flows as
    	 *  and when new FlowsFromSOL() objects are available in the BlockingQueue.
    	 */
    	
        long startime_overall = System.currentTimeMillis();
        JSONArray arr = getFlowsFromSol();
        
        /* Creating worker threads */
        for (int i = 1; i <= MAXTHREADS; i++) {
            Thread t = new Thread(new Worker("Thread" + String.valueOf(i)));
            tlist.add(t);
            t.start();
        }
        
        /*Processing and parsing all SOL flows into JAVA object. */
        processFlowsFromSol(arr);

        /* Waiting for all threads to exit. */
        for (Thread t : tlist) {
            t.join(10);
            t.interrupt();
        }
        
        endtime = System.currentTimeMillis(); // For benchmarking purpose
        System.out.println("\n\n\nInstalled " + numFlows + " flows!");
        System.out.println("\n\n\nRule install time = " + (endtime - starttime) + " msecs!\n\n\n");
        System.out.println("\n\n\nOverall Time = " + (endtime - startime_overall) + " msecs!\n\n\n");
    }

    public class FlowFromSol {
    	
    	/* This class is used to generate JAVA flow objects which are then used by the threads
    	 *  to install ODL flows.
    	 */
        String flowName;
        String outPort;
        String ethDst;
        String etherType;
        String ethSrc;
        String nodeId;
        String inPort;
        String installHw;
        String priority;
        String flowId;
        String srcIpPrefix;
        String dstIpPrefix;
    }

    public class InstallFlowThread {
    	
    	/* This is the class which is sued by the threads to install flows in ODL.
    	 *  Function: install() : Used to install flows in ODL by creating a match object
    	 *  and ODL Flow object.
    	 */
        FlowFromSol f;

        public InstallFlowThread(FlowFromSol f) {
            this.f = f;
        }

        public void install() {
        	
        	/* Used to install flows in ODL by creating a match object
        	 *  and ODL Flow object.
        	 */
        	
        	
        	/* Finding the node in which the flow needs to be installed. */
            Node node;
            node = Node.fromString(f.nodeId);
            
            /* Creating Match rules */
            Match match = new Match();
            match.setField(MatchType.DL_TYPE, Short.parseShort(f.etherType));
            match.setField(MatchType.DL_DST, stringToByteMac(f.ethDst));
            match.setField(MatchType.DL_SRC, stringToByteMac(f.ethSrc));
            match.setField(MatchType.IN_PORT, stringToNodeConnector(node, f.inPort));
            match.setField(MatchType.NW_SRC, stringToInetAddress(f.srcIpPrefix), convertMaskToInet(f.srcIpPrefix));
            match.setField(MatchType.NW_DST, stringToInetAddress(f.dstIpPrefix), convertMaskToInet(f.dstIpPrefix));

            /* Creating action for the above matches */
            List<Action> actions = new LinkedList<Action>();
            NodeConnector nodeConn = stringToNodeConnector(node, f.outPort);
            actions.add(new Output(nodeConn));

            /* Installing flows in Openflow switches. */
            Flow flow = new Flow(match, actions);
            flow.setId(Long.parseLong(f.flowId));
            flow.setPriority(Short.parseShort(f.priority));
            Status status = flowProgrammerService.addFlow(node, flow);
            if (!status.isSuccess()) {
                log.error("Could not program flow: " + status.getDescription());
                System.out.println("Could not program flow in node " + node.toString() + ": " + status.getDescription());
            }
        }
    }

    public class Worker implements Runnable {
    	
    	/* Thread class reponsible for installing flows in Openflow switches*/
        String threadName;

        public Worker(String tname) {
            threadName = tname;
        }

        public void run() {
            while (countFlows < numFlows) {
                InstallFlowThread t;
                try {
                    t = flowThreadList.take();
                    countFlows++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
                //System.out.println("Countflows="+countFlows+" numFlows="+numFlows);
                t.install();
            }
            //System.out.println("Countflows ="+countFlows+" "+threadName+" is exitting!");
        }
    }
}
