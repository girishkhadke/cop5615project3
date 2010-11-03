import java.io.*;
import java.util.*;
import java.net.*;


/**
 *
 * Created By: Girish Khadke
 * Date: 13 Sept 2010
 */

enum ERRORCODE { PORT_IN_USE, INVALID_MSG, UNKNOWN_MSG_AT_SERVER, SOCKET_IO_ERR, FILE_IO_ERR, UNRECOGNIZED_REQ_ERR, NO_ERROR};

class QueueItem
{
    public int iPriority;
    public String strFileName;
    public String strOperation;
    public Date dtStartTime;

    QueueItem()
    {
        this.iPriority = 0;
        this.strFileName = null;
        this.strOperation = null;
        this.dtStartTime = null;
    }

    QueueItem(int argPriority, String argFileName, String argOperation, Date argStartTime)
    {
        this.iPriority = argPriority;
        this.strFileName = argFileName;
        this.strOperation = argOperation;
        this.dtStartTime = argStartTime;
    }
}

class PriorityQueueComparator implements Comparator<QueueItem>
{
    public int compare(QueueItem x, QueueItem y)
    {
            String str_fileName1;
            String str_fileName2;
            String str_Operation1;
            String str_Operation2;
            int i_Priority1 = 0;
            int i_Priority2 = 0;
            str_fileName1 = x.strFileName;
            str_fileName2 = y.strFileName;
            str_Operation1 = x.strOperation;
            str_Operation2 = y.strOperation;
            i_Priority1 = x.iPriority;
            i_Priority2 = y.iPriority;
            Date startTime1 = x.dtStartTime;
            Date startTime2 = y.dtStartTime;

            if(i_Priority1 > i_Priority2)
            {
                return -1;
            }
            else if(i_Priority1 < i_Priority2)
            {
                return 1;
            }
            else if(i_Priority1 == i_Priority2)
            {
                if(startTime1.compareTo(startTime2) < 0)    //CurTime1 < curTime2
                    return -1;
                else if(startTime1.compareTo(startTime2) > 0)   //CurTime1 > curTime2
                    return 1;
                else
                    return 0;
            }
            return 0;
    }
}

class QueueTableValue
{
    public String curOperation = "nothing";
    public int iReaderCount = 0;
    public PriorityQueue<QueueItem> fileQueue;

    QueueTableValue()
    {
        this.curOperation = "nothing";
        this.fileQueue = null;
        this.iReaderCount = 0;
    }

    QueueTableValue(String curOp, PriorityQueue<QueueItem> p)
    {
        this.curOperation = curOp;
        this.fileQueue = p;
        this.iReaderCount = 0;
    }
}

public class server
{    
    private ServerSocket servSock = null;
    private static Hashtable<String,OpenFiles> hshtblOpenFileList = null;    
    private static Hashtable<String,QueueTableValue> hshQueueTbl = null;

    server()
    {
        hshtblOpenFileList = new Hashtable<String, OpenFiles>();
        hshQueueTbl = new Hashtable<String,QueueTableValue>();
    }

    public static void main(String args[])
    {
        boolean listening = true;
        try {
            server serv = new server();     //Initialize Hashtable and Priority Queue

            serv.servSock = new ServerSocket(0);
            //serv.servSock = new ServerSocket(45678);
            System.out.println("Server is listening at Port No: " + serv.servSock.getLocalPort());

            ServerProtocol.hashOpenList = hshtblOpenFileList;
            ServerProtocol.hshQueueTable = hshQueueTbl;

            int clientNumber = 0;
            while(listening)
            {
                Socket frmClientSocket = serv.servSock.accept();
                System.out.println("Server is connected to client number : " + ++clientNumber);
                new ServerProtocol(frmClientSocket).start();
            }

            serv.servSock.close();  //Need to close this after loop                                   

        } catch (BindException ex) {
            System.err.println("Bind exception thrown!!" + ex.getMessage());
            System.exit(0);
        } catch (IOException ex) {
            System.err.println("IO exception thrown!!" + ex.getMessage());
            System.exit(0);
        } catch (Exception ex) {
            System.err.println("Exception thrown!!" + ex.getMessage());
            System.exit(0);
        }
    }
}



class ServerProtocol extends Thread
{
    private Socket clientSock = null;
    private Random randGenerate = null;
    private static Integer i_Req_No;
    private static Integer i_File_No;
    //List of open files
    public static Hashtable<String,OpenFiles> hashOpenList = null;
    public static Hashtable<String,QueueTableValue> hshQueueTable = null;

    private ConfigFileParser cfp = null;
    private LogWriter log = null;
    private LogWriter logProcesses = null;
    
    ServerProtocol(Socket clientSock)
    {
        super("ServerProtocol");
        cfp = new ConfigFileParser(System.getProperty("user.dir") + File.separator + "config.ini");
        this.clientSock = clientSock;
        this.randGenerate = new Random();
        GetRandNumbers();
        this.log =  new LogWriter(System.getProperty("user.dir") + File.separator + cfp.get_ServerName() + ".log");
        this.logProcesses =  new LogWriter(System.getProperty("user.dir") + File.separator + cfp.get_ServerName() + "_Process.log");
    }

    public void run()
    {
        BufferedReader serverReader = null;
        try {            
            serverReader = new BufferedReader(new InputStreamReader(clientSock.getInputStream()));
            while (true) {
                String readLine;
                while ((readLine = serverReader.readLine()) != null)
                {
                    String[] arr_str_Split = readLine.split(" ");
                    if (arr_str_Split.length > 1) {
                        if (arr_str_Split[1].equalsIgnoreCase("Hello")) {
                            HandleHello(readLine);
                        } else if (arr_str_Split[1].equalsIgnoreCase("list")) {
                            System.out.println("list received");
                            HandleDirectoryList(readLine);
                        } else if (arr_str_Split[1].equalsIgnoreCase("delete")) {
                            System.out.println("delete received");
                            HandleDelete(readLine);
                        } else if (arr_str_Split[1].equalsIgnoreCase("put")) {
                            System.out.println("put received");
                            HandlePut(readLine);
                        } else if (arr_str_Split[1].equalsIgnoreCase("Push")) {
                            HandlePush(readLine);
                        } else if (arr_str_Split[1].equalsIgnoreCase("get")) {
                            System.out.println("get received");
                            HandleGet(readLine);
                        } else if (arr_str_Split[1].equalsIgnoreCase("pull")) {
                            HandlePull(readLine);
                        } else if (arr_str_Split[1].equalsIgnoreCase("abort")) {
                            System.out.println("abort received");
                            HandleAbort(readLine);
                        } else if (arr_str_Split[1].equalsIgnoreCase("bye")) {
                            System.out.println("terminate received and socket for client is closed only");
                            HandleBye(readLine);
                            clientSock.close();
                            //Need to insert break
                            break;  //Break Inner while loop
                        }
                    }
                }
                break;      //Once all
            }
        } catch (IOException ex) {
            System.err.println("IO exception thrown!!" + ex.getMessage());
            System.exit(0);
        } catch (Exception ex) {
            System.err.println("Exception thrown!!" + ex.getMessage());
            System.exit(0);
        }
    }

    private void AddToHashTableOpenFile(String str_Key, String str_FileName, String str_Operation, int iPriority)
    {
        OpenFiles of = new OpenFiles(str_FileName, str_Operation, iPriority);
        synchronized(hashOpenList)
        {
            hashOpenList.put(str_Key, of);
        }
    }

    private String GetOpenFileName(String str_searchKey)
    {        
        OpenFiles of = new OpenFiles();
        synchronized(hashOpenList)
        {
            of = hashOpenList.get(str_searchKey);
        }
        return of.str_OpenFileName;
    }

    private String GetOperationOnFile(String str_searchKey)
    {
        OpenFiles of = new OpenFiles();
        synchronized(hashOpenList)
        {
            of = hashOpenList.get(str_searchKey);
        }
        return of.str_Operation;
    }

    public void GetRandNumbers()
    {
        this.i_Req_No = randGenerate.nextInt(100000);
        this.i_File_No = randGenerate.nextInt(100000);
    }
    
    public void SendErrorReply(String str_requestLine, ERRORCODE errCode)
    {
        StringBuffer str_To_Send = null;
        StringBuffer str_Received = null;
        PrintWriter responseWriter = null;
        try
        {
            str_To_Send = new StringBuffer();
            str_Received = new StringBuffer();
            responseWriter = new PrintWriter(clientSock.getOutputStream(), true);
            str_Received.append(str_requestLine);

            String arr_str_Split[] = str_Received.toString().split(" ");

            if(arr_str_Split[1].equalsIgnoreCase("Hello"))
            {
                //Send hello reply
                int iReqNo = Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client
                str_To_Send.setLength(0);
                str_To_Send.append("Rsp Hello").append(" ").append(iReqNo).append(" FAILURE ").append(errCode.ordinal());                
            }
            else if(arr_str_Split[1].equalsIgnoreCase("get"))
            {
                int iReqNo = Integer.parseInt(arr_str_Split[2]);  //Get the request number from received data
                String str_fileName = arr_str_Split[3];
                str_To_Send.setLength(0);
                str_To_Send.append("Rsp get").append(" ").append(iReqNo).append(" ");
                str_To_Send.append(str_fileName).append(" ");
                str_To_Send.append("FAILURE").append(" ");
                str_To_Send.append(errCode.ordinal());
            }
            else if(arr_str_Split[1].equalsIgnoreCase("pull"))
            {
                int iReqNo = Integer.parseInt(arr_str_Split[2]);  //Get the request number from received data
                int iFileNo = Integer.parseInt(arr_str_Split[3]); //Get file numberfrom received data
                String searchKey = Integer.toString(iReqNo) + "#" + Integer.toString(iFileNo);
                //Check from List of Open Files on server, same file is open or not
                //If yes remove it from that list
                synchronized(hashOpenList)
                {
                    if (hashOpenList.containsKey(searchKey))
                    {
                        hashOpenList.remove(searchKey);
                    }
                }                
                str_To_Send.append("Rsp pull").append(" ").append(iReqNo).append(" ");
                str_To_Send.append("FAILURE").append(" ").append(errCode.ordinal());
            }
            else if(arr_str_Split[1].equalsIgnoreCase("put"))
            {
                int iReqNo = Integer.parseInt(arr_str_Split[2]);  //Get the request number from received data
                String searchKey = iReqNo + "#" + i_File_No;      //File number will be chosen by server for put
                String str_fileName = arr_str_Split[3];
                synchronized(hashOpenList)
                {
                    if (hashOpenList.containsKey(searchKey))
                    {
                        hashOpenList.remove(searchKey);
                    }
                }                
                str_To_Send.append("Rsp put").append(" ").append(iReqNo).append(" ");
                str_To_Send.append(str_fileName).append(" ");
                str_To_Send.append("FAILURE").append(" ").append(errCode.ordinal());
            }
            else if(arr_str_Split[1].equalsIgnoreCase("push"))
            {                                         
                int iReqNo = Integer.parseInt(arr_str_Split[2]);  //Get the request number from received data
                int iFileNo = Integer.parseInt(arr_str_Split[3]); //Get file number from received data                
                String searchKey = iReqNo + "#" + Integer.toString(iFileNo);
                //Search in Table of Open files on server
                synchronized(hashOpenList)
                {
                    if (hashOpenList.containsKey(searchKey))
                    {
                        //String fileName = hashOpenList.get(searchKey);
                        String fileName = GetOpenFileName(searchKey);   //Get OpenFile Name from hashTable
                        String str_FilePath = System.getProperty("user.dir") + File.separator + fileName;
                        //Search if *.bak exists for this file, if yes restore bak
                        File fCheck = new File(str_FilePath + ".bak");
                        if(fCheck.exists())
                        {
                            //*.bak file is renamed and old file is restored
                            File old = new File(str_FilePath + ".bak");
                            old.renameTo(new File(str_FilePath));
                        }
                        hashOpenList.remove(searchKey);
                    }
                }                
                str_To_Send.append("Rsp push").append(" ").append(iReqNo).append(" ");
                str_To_Send.append("FAILURE").append(" ").append(errCode.ordinal());
            }
            else if(arr_str_Split[1].equalsIgnoreCase("delete"))
            {
                int iReqNo = Integer.parseInt(arr_str_Split[2]);
                String str_fileName = arr_str_Split[3];
                str_To_Send.append("Rsp delete").append(" ").append(iReqNo).append(" ");
                str_To_Send.append(str_fileName).append(" ");
                str_To_Send.append("FAILURE").append(" ").append(errCode.ordinal());                
            }
            else if(arr_str_Split[1].equalsIgnoreCase("list"))
            {
                int iReqNo = Integer.parseInt(arr_str_Split[2]);
                str_To_Send.append("Rsp list").append(" ").append(iReqNo).append(" ");
                str_To_Send.append("FAILURE").append(" ").append(errCode.ordinal());
            }            
            
            responseWriter.println(str_To_Send.toString());
            responseWriter.flush();
        }
        catch (IOException ex) {
          System.err.println("Error Occured in SendErrorReply routine");
          System.exit(0);
        } catch (Exception ex) {
          System.err.println("Error Occured in SendErrorReply routine");
          System.exit(0);
        }
        finally
        {
            str_To_Send = str_Received = null;
        }        
    }
        
    public void HandleHello(String str_requestLine) {
        StringBuffer str_To_Send = null;
        StringBuffer str_Received = null;
        PrintWriter responseWriter = null;
        boolean isError = false;
        ERRORCODE enumerrCode = ERRORCODE.NO_ERROR;
        try {
            str_To_Send = new StringBuffer();
            str_Received = new StringBuffer();
            responseWriter = new PrintWriter(clientSock.getOutputStream(), true);

            str_Received.append(str_requestLine);
            //System.out.println("Client Server: " + str_Received.toString());
            log.WriteToLogFile(str_Received.toString());

            String arr_str_Split[] = str_Received.toString().split(" ");
            i_Req_No = Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client

            //Send hello reply
            str_To_Send.append("Rsp Hello").append(" ").append(i_Req_No).append(" ").append("SUCCESS");
            responseWriter.println(str_To_Send.toString());
            responseWriter.flush();

            log.WriteToLogFile(str_To_Send.toString());
            
        } catch (IOException ex) {
            enumerrCode = ERRORCODE.SOCKET_IO_ERR;
            isError = true; 
        } catch (Exception ex) {
            enumerrCode = ERRORCODE.UNRECOGNIZED_REQ_ERR;
            isError = true; 
        }
        finally
        {
            //If error thrown
            if(isError)
            {
                //Send Hello reply with Failure code
                SendErrorReply(str_requestLine,enumerrCode);
            }
        }
    }

    
    public void HandleBye(String str_requestLine) {
        StringBuffer str_To_Send = null;
        StringBuffer str_Received = null;
        PrintWriter responseWriter = null;
        try {
            str_To_Send = new StringBuffer();
            str_Received = new StringBuffer();
            responseWriter = new PrintWriter(clientSock.getOutputStream(), true);

            str_Received.append(str_requestLine);
            //System.out.println("Client Server: " + str_Received.toString());
            log.WriteToLogFile(str_Received.toString());
            
            //Send bye reply
            str_To_Send.append("Rsp bye").append(" ");
            responseWriter.println(str_To_Send.toString());
            responseWriter.flush();
            
            log.WriteToLogFile(str_To_Send.toString());
            
        } catch (IOException ex) {
            System.out.println("IO Exception occured!!");
            System.exit(0);
        } catch (Exception ex) {
            System.out.println("Exception occured!!");
            System.exit(0);
        }
    }

    public void AwakeProcess(String strFileName)
    {
        boolean isNotifyRequired = false;
        QueueTableValue qtv = null;
        QueueItem qitem = null;
        synchronized(hshQueueTable)
        {
            qtv = (QueueTableValue) hshQueueTable.get(strFileName);

            //Complete all readers outside queue first (RRRW)
            if(qtv.curOperation.equalsIgnoreCase("read"))
            {
                if(qtv.iReaderCount != 0)   
                {
                    return;
                }
            }
            
            qtv.curOperation = "nothing";
            if(!qtv.fileQueue.isEmpty())
            {
                qitem = (QueueItem) qtv.fileQueue.poll();
                qtv.curOperation = qitem.strOperation;
                isNotifyRequired = true;
            }
            else
            {
                qtv.curOperation = "nothing";
                isNotifyRequired = false;
            }                       
        }
        if(qitem!=null)
        {
            synchronized(qitem)
            {
                if(isNotifyRequired)
                {
                    qitem.notify();
                }
            }
        }
    }

    public void HandleGet(String str_requestLine)
    {
        StringBuffer str_To_Send = null;
        StringBuffer str_Received = null;
        String str_Fname = null;
        String searchKey = null;
        PrintWriter responseWriter = null;
        String str_FilePath = null;
        boolean isFileExists = false;
        String isReady = "FAILURE";
        boolean isError = false;
        ERRORCODE enumerrCode = ERRORCODE.NO_ERROR;
        int i_priority = 0;
        boolean isWaitRequired = false;
        int ReqNo, FileNo;
        boolean isAwakeAfterFileNotExist = false;
        try {
            str_To_Send = new StringBuffer();
            str_Received = new StringBuffer();

            responseWriter = new PrintWriter(clientSock.getOutputStream(), true);

            str_Received.append(str_requestLine);
            str_Received.append(" ").append(Calendar.getInstance().getTime());

            String arr_str_Split[] = str_Received.toString().split(" ");
            i_Req_No = Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client
            ReqNo = Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client
            FileNo = randGenerate.nextInt(100000);      //Get random file number
            str_Fname = arr_str_Split[3];                   //Get file name
            i_priority = Integer.parseInt(arr_str_Split[4]);  //Get priority
            str_FilePath = System.getProperty("user.dir") + File.separator + str_Fname;

            QueueTableValue qtv = null;
            QueueItem qitem = null;
            
            log.WriteToLogFile(str_Received.toString());

            synchronized(hshQueueTable)
            {
                if(hshQueueTable.containsKey(str_Fname))  //Contains key means Queue created already
                {
                    qtv = (QueueTableValue)hshQueueTable.get(str_Fname);
                    if(qtv.curOperation.equalsIgnoreCase("nothing"))
                    {   //Queue might also be empty
                        //Set CurOperation to read and proceed with read
                        qtv.curOperation = "read";
                        qtv.iReaderCount++;
                    }
                    else if(qtv.curOperation.equalsIgnoreCase("read"))
                    {
                        if(qtv.fileQueue.isEmpty()) //Queue empty means no Higher priority process (writer or delete) waiting
                        {
                            //Allow read access
                            qtv.curOperation = "read";
                            qtv.iReaderCount++;
                        }
                        else    //Queue not empty means there is writer or delete in queue
                        {
                            //Check if higher priority
                            if(qtv.fileQueue.peek().iPriority > i_priority && ( qtv.fileQueue.peek().strOperation.equalsIgnoreCase("write") || qtv.fileQueue.peek().strOperation.equalsIgnoreCase("delete") ))
                            {   //Higher priority writer/delete in queue at top
                                //So add read to queue and wait
                                qitem = new QueueItem(i_priority,str_Fname,"read",Calendar.getInstance().getTime());
                                qtv.fileQueue.add(qitem);
                                isWaitRequired = true;
                            }
                            else
                            {
                                //Allow read access
                                qtv.curOperation = "read";
                                qtv.iReaderCount++;
                            }
                        }
                    }
                    else if(qtv.curOperation.equalsIgnoreCase("write"))
                    {
                        //Add new Operation to queue
                        qitem = new QueueItem(i_priority,str_Fname,"read",Calendar.getInstance().getTime());
                        qtv.fileQueue.add(qitem);
                        //After pushing item to queue , wait
                        isWaitRequired = true;
                    }
                    else if(qtv.curOperation.equalsIgnoreCase("delete"))
                    {
                        //Add new Operation to queue
                        qitem = new QueueItem(i_priority,str_Fname,"read",Calendar.getInstance().getTime());
                        qtv.fileQueue.add(qitem);
                        //After pushing item to queue , wait
                        isWaitRequired = true;
                    }                    
                }
                else        //Means first request for this file and queue is not created
                {
                    //Add entry to hshQueue table with file name and empty queue and proceed with get(read)
                    Comparator<QueueItem> comparator = new PriorityQueueComparator();
                    PriorityQueue<QueueItem> pq = new PriorityQueue<QueueItem>(50,comparator);
                    qtv = new QueueTableValue("read", pq);
                    qtv.curOperation = "read";
                    qtv.iReaderCount++;
                    hshQueueTable.put(str_Fname, qtv);
                    //Allow read
                }
            }

            if(qitem!=null)
            {
                synchronized(qitem)
                {
                    if(isWaitRequired)
                    {
                            StringBuffer waitMsg = new StringBuffer();
                            waitMsg.setLength(0);
                            waitMsg.append("\nTime: ").append(Calendar.getInstance().getTime());
                            waitMsg.append(" --->Reader with priority: ").append(i_priority).append(" waiting...\n");
                            System.out.println(waitMsg.toString());
                            logProcesses.WriteToLogFile(waitMsg.toString());

                            waitMsg.setLength(0);
                            waitMsg.append("Rsp get ").append(ReqNo).append(" ").append(str_Fname).append(" WAIT ");
                            //Send wait msg
                            responseWriter.println(waitMsg.toString());
                            responseWriter.flush();

                            qitem.wait();   //Call wait

                            //Log wait message on server after waiting done (to avoid cluttered log)
                            log.WriteToLogFile(waitMsg.toString());
                            isWaitRequired = false;
                    }
                }
            }

            StringBuilder runMsg = new StringBuilder();
            runMsg.setLength(0);
            runMsg.append("\nTime: ").append(Calendar.getInstance().getTime());
            runMsg.append(" --->Reader with priority: ").append(i_priority).append(" running...\n");
            System.out.println(runMsg.toString());
            logProcesses.WriteToLogFile(runMsg.toString());

            File fExist = new File(str_FilePath);   //Check if file exists on server
            if(fExist.exists())
            {
                isFileExists = true;
            }
            if(isFileExists)
                isReady = "READY";
            else
                isReady = "FAILURE";
            
            //Send get reply
            str_To_Send.append("Rsp get").append(" ").append(ReqNo).append(" ");
            str_To_Send.append(str_Fname).append(" ");
            str_To_Send.append(isReady).append(" ");
            if(isReady.equalsIgnoreCase("READY"))
            {
                   str_To_Send.append(FileNo);       //File number chosen by server
                   //Add entry to hashtable to indicate list of open files on server
                    searchKey = Integer.toString(ReqNo)  + "#" + Integer.toString(FileNo);
                    AddToHashTableOpenFile(searchKey , str_Fname , "get" , i_priority);  // Get operation has opened file
            }
            else
            {
                //ERROR FILE DOES NOT EXIST SO Decrement ReadCount
                synchronized(hshQueueTable)
                {
                    if(hshQueueTable.containsKey(str_Fname))  //Contains key means Queue created already
                    {
                        QueueTableValue qtvalue = null;
                        qtvalue = (QueueTableValue)hshQueueTable.get(str_Fname);
                        if(qtvalue.iReaderCount!=0)
                            qtvalue.iReaderCount--;
                        isAwakeAfterFileNotExist = true;
                    }
                }

                StringBuilder doneMsg = new StringBuilder();
                doneMsg.setLength(0);

                doneMsg.append("\nTime: ").append(Calendar.getInstance().getTime());
                doneMsg.append(" --->Reader with priority: ").append(i_priority).append(" done with file not exists...\n");
                System.out.println(doneMsg.toString());
                logProcesses.WriteToLogFile(doneMsg.toString());
                
                enumerrCode = ERRORCODE.FILE_IO_ERR;
                str_To_Send.append(enumerrCode.ordinal());  //Failure code for File IO
            }
            responseWriter.println(str_To_Send.toString());
            responseWriter.flush();

            log.WriteToLogFile(str_To_Send.toString());

            if(isAwakeAfterFileNotExist)
            {
                AwakeProcess(str_Fname);  //Awake required after get failure (RDRR)
            }
        
        } catch(FileNotFoundException fe) {
            isError = true; enumerrCode = ERRORCODE.FILE_IO_ERR;
        } catch (IOException ex) {
            enumerrCode = ERRORCODE.SOCKET_IO_ERR;
            isError = true; 
        } catch (Exception ex) {
            enumerrCode = ERRORCODE.UNKNOWN_MSG_AT_SERVER;
            isError = true; 
        }
        finally
        {
            //If error thrown
            if(isError)
            {
                //Send Get reply with Failure code
                SendErrorReply(str_requestLine,enumerrCode);
            }
        }
    }
    
     public void HandlePull(String str_requestLine)
     {
        boolean isLastData = false;        
        StringBuffer str_To_Send = new StringBuffer();
        StringBuffer str_Received = new StringBuffer();
        String str_Fname = null;
        String str_FilePath = null;
        String searchKey = null;
        String isLast = "NOTLAST";
        RandomAccessFile randFile = null;
        PrintWriter responseWriter = null;
        StringBuffer printBuf = null;
        boolean isError = false;
        ERRORCODE enumerrCode = ERRORCODE.NO_ERROR;
        int ReqNo, FileNo;
        try {            
            str_Received.append(str_requestLine);
            String arr_str_Split[] = str_Received.toString().split(" ");
            i_Req_No = Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client
            ReqNo = Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client
            i_File_No = Integer.parseInt(arr_str_Split[3]);
            FileNo = Integer.parseInt(arr_str_Split[3]);
            searchKey = Integer.toString(ReqNo) + "#" + Integer.toString(FileNo);

            synchronized(hashOpenList)
            {
                //Check from List of Open Files on server, same file is open or not
                if (hashOpenList.containsKey(searchKey)) {
                    str_Fname = GetOpenFileName(searchKey);
                } else {
                    str_Fname = "";
                    //File not found in OpenFiles
                }
            }

            str_FilePath = System.getProperty("user.dir") + File.separator + str_Fname;

            log.WriteToLogFile(str_Received.toString());

            //Open file in read mode
            randFile = new RandomAccessFile(str_FilePath, "r");
            responseWriter = new PrintWriter(clientSock.getOutputStream(), true);
            
            int iFromByte = Integer.parseInt(arr_str_Split[4]);
            int iLength = Integer.parseInt(arr_str_Split[5]);
            byte readBytes[] = new byte[iLength];

            //Seek to required byte and read
            randFile.seek(iFromByte);
            int actualBytesRead = randFile.read(readBytes);
            byte [] readNewBytes;
            
            if((iFromByte + iLength) >= randFile.length())
            {
                readNewBytes = new byte[actualBytesRead];
                System.arraycopy(readBytes, 0, readNewBytes, 0, actualBytesRead);
                isLast = "LAST";
                isLastData = true;
                readBytes = readNewBytes;
                iLength = readBytes.length;
            }
            else
            {
                isLast = "NOTLAST";
                isLastData = false;
            }

            //Encode to string 
            String dataToSend = encodeToString(readBytes);

            //Send pull reply
            str_To_Send.append("Rsp pull").append(" ").append(ReqNo).append(" ");
            str_To_Send.append("SUCCESS").append(" ");
            str_To_Send.append(isLast).append(" ").append(iFromByte).append(" ");
            str_To_Send.append(iLength).append(" ").append(dataToSend);            
            responseWriter.println(str_To_Send.toString());
            responseWriter.flush();

            printBuf = new StringBuffer();
            printBuf.append("Rsp pull").append(" ").append(ReqNo).append(" ");
            printBuf.append("SUCCESS").append(" ");
            printBuf.append(isLast).append(" ").append(iFromByte).append(" ");
            printBuf.append(iLength).append(" ").append("<").append(actualBytesRead).append(">");            

            log.WriteToLogFile(printBuf.toString());

            if(isLastData)      //after giving last chunk, remove file from open list of files
            {
                if(randFile!=null)
                    randFile.close();           //Closing streams is important
                OpenFiles openCheck = null;
                int curPriority = 0;
                synchronized(hashOpenList)
                {                    
                    openCheck = (OpenFiles)hashOpenList.get(searchKey);
                    curPriority = openCheck.i_Priority;
                    hashOpenList.remove(searchKey); //Once last data chunk of File is completely written, remove entry
                }

                synchronized(hshQueueTable)
                {
                    QueueTableValue qtval = null;
                    if(hshQueueTable.containsKey(str_Fname))
                    {
                        qtval = (QueueTableValue)hshQueueTable.get(str_Fname);
                        if(qtval.iReaderCount!=0)
                            qtval.iReaderCount--;
                    }                    
                }

                StringBuilder doneMsg = new StringBuilder();
                doneMsg.setLength(0);
                doneMsg.append("\nTime: ").append(Calendar.getInstance().getTime());
                doneMsg.append(" --->Reader with priority: ").append(curPriority).append(" done...\n");
                System.out.println(doneMsg.toString());
                logProcesses.WriteToLogFile(doneMsg.toString());

                //Call AwakeProcessMethod
                AwakeProcess(str_Fname);        //awake process waiting on file queue
            }
            if(randFile!=null)
                randFile.close();           //Closing streams is important

        } catch(FileNotFoundException fe) {
            isError = true; enumerrCode = ERRORCODE.FILE_IO_ERR;
        } catch (IOException ex) {
            isError = true; enumerrCode = ERRORCODE.SOCKET_IO_ERR;
        } catch (Exception ex) {
            isError = true; enumerrCode = ERRORCODE.UNKNOWN_MSG_AT_SERVER;
        }
        finally
        {
            //If error thrown
            if(isError)
            {
                //Send Get reply with Failure code
                SendErrorReply(str_requestLine,enumerrCode);
            }
        }
    }

    public void HandlePut(String str_requestLine)
    {
        StringBuffer str_To_Send = null;
        StringBuffer str_Received = null;
        String str_Fname = null;
        String searchKey = null;
        PrintWriter responseWriter = null;
        String str_FilePath = null;
        boolean renamedOldFile = false;
        boolean isError = false;
        ERRORCODE enumerrCode = ERRORCODE.NO_ERROR;
        int i_priority = 0;
        boolean isWaitRequired = false;
        int ReqNo, FileNo;
        try {
            str_To_Send = new StringBuffer();
            str_Received = new StringBuffer();
            responseWriter = new PrintWriter(clientSock.getOutputStream(), true);

            str_Received.append(str_requestLine);  
            str_Received.append(" ").append(Calendar.getInstance().getTime());

            String arr_str_Split[] = str_Received.toString().split(" ");
            i_Req_No = Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client
            ReqNo =  Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client
            str_Fname = arr_str_Split[3];
            i_priority = Integer.parseInt(arr_str_Split[4]);  //Get priority
            
            str_FilePath = System.getProperty("user.dir") + File.separator + str_Fname;
          
            log.WriteToLogFile(str_Received.toString());

            QueueTableValue qtv = null;
            QueueItem qitem = null;
            synchronized(hshQueueTable)
            {
                if(hshQueueTable.containsKey(str_Fname))  //Contains key means Queue created already
                {
                    qtv = (QueueTableValue)hshQueueTable.get(str_Fname);
                    if(qtv.curOperation.equalsIgnoreCase("nothing"))
                    {   //Queue might also be empty
                        //Set CurOperation to write and proceed with write
                        qtv.curOperation = "write";
                        //Allow write
                    }
                    else if(qtv.curOperation.equalsIgnoreCase("read"))
                    {
                        //Add new Operation to queue
                        qitem = new QueueItem(i_priority,str_Fname,"write",Calendar.getInstance().getTime());
                        qtv.fileQueue.add(qitem);
                        //After pushing item to queue , wait
                        isWaitRequired = true;
                    }
                    else if(qtv.curOperation.equalsIgnoreCase("write"))
                    {
                        //Add new Operation to queue
                        qitem = new QueueItem(i_priority,str_Fname,"write",Calendar.getInstance().getTime());
                        qtv.fileQueue.add(qitem);
                        //After pushing item to queue , wait
                        isWaitRequired = true;
                    }
                    else if(qtv.curOperation.equalsIgnoreCase("delete"))
                    {
                        //Add new Operation to queue
                        qitem = new QueueItem(i_priority,str_Fname,"write",Calendar.getInstance().getTime());
                        qtv.fileQueue.add(qitem);
                        //After pushing item to queue , wait
                        isWaitRequired = true;
                    }                    
                }
                else        //Means first request for this file and queue is not created
                {
                    //Add entry to hshQueue table with file name and empty queue and proceed with put(write)
                    Comparator<QueueItem> comparator = new PriorityQueueComparator();
                    PriorityQueue<QueueItem> pq = new PriorityQueue<QueueItem>(50,comparator);
                    qtv = new QueueTableValue("write", pq);
                    qtv.curOperation = "write";
                    hshQueueTable.put(str_Fname, qtv);
                }
            }

            if(qitem!=null)
            {
                synchronized(qitem)
                {
                    if(isWaitRequired)
                    {
                            StringBuffer waitMsg = new StringBuffer();
                            waitMsg.setLength(0);

                            waitMsg.append("\nTime: ").append(Calendar.getInstance().getTime());
                            waitMsg.append(" --->Writer with priority: ").append(i_priority).append(" waiting...\n");
                            System.out.println(waitMsg.toString());
                            logProcesses.WriteToLogFile(waitMsg.toString());

                            waitMsg.setLength(0);
                            waitMsg.append("Rsp put ").append(ReqNo).append(" ").append(str_Fname).append(" WAIT ");

                            //send wait msg
                            responseWriter.println(waitMsg.toString());
                            responseWriter.flush();
                            
                            qitem.wait();   //Call wait
                            
                            //Log wait msg on server after wait to avoid cluttered log
                            log.WriteToLogFile(waitMsg.toString());
                            isWaitRequired = false;
                    }
                }
            }

            StringBuilder runMsg = new StringBuilder();
            runMsg.setLength(0);
            runMsg.append("\nTime: ").append(Calendar.getInstance().getTime());
            runMsg.append(" --->Writer with priority: ").append(i_priority).append(" running...\n");
            System.out.println(runMsg.toString());
            logProcesses.WriteToLogFile(runMsg.toString());

            //Send put reply
            str_To_Send.append("Rsp put").append(" ").append(ReqNo).append(" ");
            str_To_Send.append(str_Fname).append(" ");
            i_File_No = randGenerate.nextInt(100000);
            FileNo = i_File_No;
            str_To_Send.append("READY").append(" ").append(FileNo);  //File no chosen by server
            responseWriter.println(str_To_Send.toString());
            responseWriter.flush();

            //Add entry to hashtable to indicate list of open file on server
            searchKey = ReqNo + "#" + FileNo;
            AddToHashTableOpenFile(searchKey, str_Fname, "put" , i_priority);  //Put has opened file

            File fCheck = new File(str_FilePath);
            if(fCheck.exists())
            {                
                //Same file exists means Rename existing file to a new extension .bak
                //In case of error while writing data for new file , revert back old file by rename
                File old = new File(str_FilePath);
                old.renameTo(new File(str_FilePath + ".bak"));  //Old file is renamed with *.bak extension
                renamedOldFile = true;
                System.out.println("File " + str_Fname + " already exists by same name: Old file is renamed with .bak extension");
            }            

            log.WriteToLogFile(str_To_Send.toString());

        } catch(FileNotFoundException fe) {
            isError = true; enumerrCode = ERRORCODE.FILE_IO_ERR;
        } catch (IOException ex) {
            isError = true; enumerrCode = ERRORCODE.SOCKET_IO_ERR;
        } catch (Exception ex) {
            isError = true; enumerrCode = ERRORCODE.UNKNOWN_MSG_AT_SERVER;
        }
        finally {
            //If error thrown
            if(isError)
            {
                //If Old file is renamed to *.bak, restore that file
                if(renamedOldFile)
                {
                    File old = new File(str_FilePath + ".bak");
                    old.renameTo(new File(str_FilePath));  //*.bak file is renamed and old file is restored                    
                    renamedOldFile = false;
                    System.out.println("Old File with same name is restored !!!");
                }
                SendErrorReply(str_requestLine, enumerrCode);
            }
        }
    }

    public void HandlePush(String str_requestLine)
    {
        boolean isLastData = false;        
        StringBuffer str_To_Send = new StringBuffer();
        StringBuffer str_Received = new StringBuffer();
        String str_Fname = null;
        String str_FilePath = null;
        String searchKey = null;
        String isLast = null;
        FileOutputStream fstream = null;
        PrintWriter responseWriter = null;
        StringBuffer printBuf = null;
        boolean isError = false;
        int ReqNo, FileNo;
        ERRORCODE enumerrCode = ERRORCODE.NO_ERROR;
        try {
            str_Received.append(str_requestLine);
            String arr_str_Split[] = str_Received.toString().split(" ");
            i_Req_No = Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client
            ReqNo = Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client
            i_File_No = Integer.parseInt(arr_str_Split[3]);  //Also get file no
            FileNo = Integer.parseInt(arr_str_Split[3]);  //Also get file no
            searchKey = Integer.toString(ReqNo) + "#" + Integer.toString(FileNo);

            synchronized(hashOpenList)
            {
                 //Search in Table of Open files on server
                if (hashOpenList.containsKey(searchKey))
                {
                    str_Fname = GetOpenFileName(searchKey);
                } else {
                    str_Fname = "";
                    //File not found in OpenFiles
                }
            }

            str_FilePath = System.getProperty("user.dir") + File.separator + str_Fname;

            isLast = arr_str_Split[4];
            if (isLast.equalsIgnoreCase("NOTLAST")) {
                isLastData = false;
            } else if (isLast.equalsIgnoreCase("LAST")) {
                isLastData = true;
            }
            
            str_Received.append(str_requestLine);        
            fstream = new FileOutputStream(str_FilePath,true);  //write File
            responseWriter = new PrintWriter(clientSock.getOutputStream(), true);

            //Read File Data From client
            int noOfByteReceived = Integer.parseInt(arr_str_Split[6]);            
            byte readBytes[] = new byte[noOfByteReceived];
            readBytes = decodeFromString(arr_str_Split[7],noOfByteReceived);
            int iData = readBytes.length;
            //Write Bytes to file on server
            fstream.write(readBytes);

            printBuf = new StringBuffer();
            printBuf.append(arr_str_Split[0]).append(" ").append(arr_str_Split[1]).append(" ");
            printBuf.append(arr_str_Split[2]).append(" ").append(arr_str_Split[3]).append(" ");
            printBuf.append(arr_str_Split[4]).append(" ").append(arr_str_Split[5]).append(" ");
            printBuf.append(noOfByteReceived).append(" ").append("<").append(iData).append(">");
            
            log.WriteToLogFile(printBuf.toString());
            
            //Send push reply
            str_To_Send.append("Rsp push").append(" ").append(ReqNo).append(" ");
            str_To_Send.append("SUCCESS").append(" ").append(noOfByteReceived);
            responseWriter.println(str_To_Send.toString());
            responseWriter.flush();
            
            log.WriteToLogFile(str_To_Send.toString());
            
            //For last chunk of data, remove file from OpenList of files and add to list of files on Disk
            if(isLastData)
            {
                if(fstream!=null)
                    fstream.close();
                int curPriority = 0;
                OpenFiles openCheck = null;
                synchronized(hashOpenList)
                {
                    openCheck = (OpenFiles)hashOpenList.get(searchKey);
                    curPriority = openCheck.i_Priority;
                   hashOpenList.remove(searchKey); //Once last data chunk of File is completely written, remove entry
                }

                File fBackup = new File(str_FilePath + ".bak");
                if(fBackup.exists()) //If .bak exists for new file, remove .bak after newfile written successfully
                {
                    fBackup.delete(); //Delete *.bak file physically since new file is written successfully
                }

                StringBuilder doneMsg = new StringBuilder();
                doneMsg.setLength(0);
                doneMsg.append("\nTime: ").append(Calendar.getInstance().getTime());
                doneMsg.append(" --->Writer with priority: ").append(curPriority).append(" done...\n");
                System.out.println(doneMsg.toString());
                logProcesses.WriteToLogFile(doneMsg.toString());

                AwakeProcess(str_Fname);    //awake process waiting on file queue
            }
            if(fstream!=null)
                fstream.close();    //Closing fstream is important to rename existing file on win

        } catch(FileNotFoundException fe) {
            isError = true; enumerrCode = ERRORCODE.FILE_IO_ERR;
        } catch (IOException ex) {
            //In case of failure message being sent to client machine
            //check isSameFileExists and previous file should be there after current push is aborted
            isError = true; enumerrCode = ERRORCODE.SOCKET_IO_ERR;
        } catch (Exception ex) {
            //In case of failure message being sent to client machine
            isError = true; enumerrCode = ERRORCODE.UNKNOWN_MSG_AT_SERVER;
        }
        finally
        {
            //If error thrown
            if(isError)
            {
                SendErrorReply(str_requestLine, enumerrCode);
            }
        }
    }

    public void HandleDelete(String str_requestLine)
    {
        StringBuffer str_To_Send = null;
        StringBuffer str_Received = null;
        PrintWriter responseWriter = null;
        String str_FileName = null;
        String isSuccess = "SUCCESS";
        String str_FilePath = null;
        boolean isError = false;
        ERRORCODE enumerrCode = ERRORCODE.NO_ERROR;
        String searchKey = null;
        int i_priority = 0;
        boolean isWaitRequired = false;
        int ReqNo, FileNo;
        try {
            str_To_Send = new StringBuffer();
            str_Received = new StringBuffer();
            responseWriter = new PrintWriter(clientSock.getOutputStream(), true);

            str_Received.append(str_requestLine);            
            str_Received.append(" ").append(Calendar.getInstance().getTime());

            String arr_str_Split[] = str_Received.toString().split(" ");
            i_Req_No = Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client
            ReqNo = Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client
            FileNo = i_File_No;
            str_FileName = arr_str_Split[3];                //Read File Name
            i_priority = Integer.parseInt(arr_str_Split[4]);    //Read Priority
            
            str_FilePath = System.getProperty("user.dir") + File.separator + str_FileName;
            searchKey = ReqNo  + "#" + FileNo;            //i_File_No random no chosen by server

            QueueTableValue qtv = null;
            QueueItem qitem = null;
            synchronized(hshQueueTable)
            {
                if(hshQueueTable.containsKey(str_FileName))  //Contains key means Queue created already
                {
                    qtv = (QueueTableValue)hshQueueTable.get(str_FileName);
                    if(qtv.curOperation.equalsIgnoreCase("nothing"))
                    {   //Queue might also be empty
                        //Set CurOperation to delete and proceed with delete
                        qtv.curOperation = "delete";
                    }
                    else if(qtv.curOperation.equalsIgnoreCase("read"))
                    {
                        //Add new Operation to queue
                        qitem = new QueueItem(i_priority,str_FileName,"delete",Calendar.getInstance().getTime());
                        qtv.fileQueue.add(qitem);
                        //After pushing item to queue , wait
                        isWaitRequired = true;
                    }
                    else if(qtv.curOperation.equalsIgnoreCase("write"))
                    {
                        //Add new Operation to queue
                        qitem = new QueueItem(i_priority,str_FileName,"delete",Calendar.getInstance().getTime());
                        qtv.fileQueue.add(qitem);
                        //After pushing item to queue , wait
                        isWaitRequired = true;
                    }
                    else if(qtv.curOperation.equalsIgnoreCase("delete"))
                    {
                        //Add new Operation to queue
                        qitem = new QueueItem(i_priority,str_FileName,"delete",Calendar.getInstance().getTime());
                        qtv.fileQueue.add(qitem);
                        //After pushing item to queue , wait
                        isWaitRequired = true;
                    }                    
                }
                else        //Means first request for this file and queue is not created
                {
                    //Add entry to hshQueue table with file name and empty queue and proceed with get(read)
                    Comparator<QueueItem> comparator = new PriorityQueueComparator();
                    PriorityQueue<QueueItem> pq = new PriorityQueue<QueueItem>(50,comparator);
                    qtv = new QueueTableValue("delete", pq);
                    qtv.curOperation = "delete";
                    hshQueueTable.put(str_FileName, qtv);
                }
            }

            if(qitem!=null)
            {
                synchronized(qitem)
                {
                    if(isWaitRequired)  //Wait required
                    {
                        StringBuffer waitMsg = new StringBuffer();
                        waitMsg.setLength(0);
                        waitMsg.append("\nTime: ").append(Calendar.getInstance().getTime());
                        waitMsg.append(" --->Delete with priority: ").append(i_priority).append(" waiting...\n");
                        System.out.println(waitMsg.toString());
                        logProcesses.WriteToLogFile(waitMsg.toString());

                        waitMsg.setLength(0);
                        waitMsg.append("Rsp delete ").append(ReqNo).append(" ").append(str_FileName).append(" WAIT ");

                        //Send wait msg
                        responseWriter.println(waitMsg.toString());
                        responseWriter.flush();
                        
                        qitem.wait();   //Call wait

                        //Log wait message on server after wait to avoid cluttered log
                        log.WriteToLogFile(waitMsg.toString());
                        isWaitRequired = false;
                    }
                }
            }
            
            log.WriteToLogFile(str_Received.toString());

            AddToHashTableOpenFile(searchKey , str_FileName , "delete" , i_priority);  // Get operation has opened file

            StringBuilder runMsg = new StringBuilder();
            runMsg.setLength(0);
            runMsg.append("\nTime: ").append(Calendar.getInstance().getTime());
            runMsg.append(" --->Delete with priority: ").append(i_priority).append(" running...\n");
            System.out.println(runMsg.toString());
            logProcesses.WriteToLogFile(runMsg.toString());


            File fExist = new File(str_FilePath);   //Check if file exists on server
            if(fExist.exists())
            {
                boolean blnSuccess = fExist.delete();   //Check if file deletion succeeded or not
                if(blnSuccess) 
                {
                    isSuccess = "SUCCESS";
                }
                else 
                {
                    isSuccess = "FAILURE";
                }
            }
            else
            {
                isSuccess = "FAILURE";
            }

            //Send delete reply
            str_To_Send.append("Rsp delete").append(" ").append(ReqNo).append(" ");
            str_To_Send.append(str_FileName).append(" ");
            str_To_Send.append(isSuccess).append(" ");
            if(isSuccess.equalsIgnoreCase("FAILURE"))
            {
                enumerrCode = ERRORCODE.FILE_IO_ERR;
                str_To_Send.append(enumerrCode.ordinal());
            }            
            responseWriter.println(str_To_Send.toString());
            responseWriter.flush();

            log.WriteToLogFile(str_To_Send.toString());

            //remove Entry from HashOpenList Once File gets deleted
            synchronized(hashOpenList)
            {
               hashOpenList.remove(searchKey); 
            }

            StringBuilder doneMsg = new StringBuilder();
            doneMsg.setLength(0);

            doneMsg.append("\nTime: ").append(Calendar.getInstance().getTime());
            doneMsg.append(" --->Delete with priority: ").append(i_priority).append(" done...\n");
            System.out.println(doneMsg.toString());
            logProcesses.WriteToLogFile(doneMsg.toString());
            
            AwakeProcess(str_FileName); //Awake Process waiting on filequeue
            
        } catch(FileNotFoundException fe) {
            isError = true; enumerrCode = ERRORCODE.FILE_IO_ERR;
        } catch (IOException ex) {
            enumerrCode = ERRORCODE.SOCKET_IO_ERR;
            isError = true;
        } catch (Exception ex) {
            enumerrCode = ERRORCODE.UNRECOGNIZED_REQ_ERR;
            isError = true;
        }
        finally
        {
            //If error thrown
            if(isError)
            {
                //Send Hello reply with Failure code
                SendErrorReply(str_requestLine,enumerrCode);
            }
        }
    }

    public void HandleDirectoryList(String str_requestLine)
    {
        StringBuffer str_To_Send = null;
        StringBuffer str_Received = null;
        PrintWriter responseWriter = null;
        int actualNoOfFiles = 0;
        int nMaxNoOfFiles = 0;
        int startFileIndex = 0;
        StringBuffer str_ListOfFiles = null;
        boolean isError = false;
        ERRORCODE enumerrCode = ERRORCODE.NO_ERROR;
        try {
            str_To_Send = new StringBuffer();
            str_Received = new StringBuffer();
            str_ListOfFiles = new StringBuffer();
            
            responseWriter = new PrintWriter(clientSock.getOutputStream(), true);

            str_Received.append(str_requestLine);
            str_Received.append(" ").append(Calendar.getInstance().getTime());
            
            //System.out.println("Client Server: " + str_Received.toString());
            log.WriteToLogFile(str_Received.toString());

            String arr_str_Split[] = str_Received.toString().split(" ");
            i_Req_No = Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client
            startFileIndex = Integer.parseInt(arr_str_Split[3]);
            nMaxNoOfFiles = Integer.parseInt(arr_str_Split[4]); //Get max number of files requested from client

            StringBuilder runMsg = new StringBuilder();
            runMsg.setLength(0);
            runMsg.append("\nDirectoryList running...\n");
            System.out.println(runMsg.toString());
            logProcesses.WriteToLogFile(runMsg.toString());

            //Get List of Files on server in a list
            File curDirectory = new File(System.getProperty("user.dir"));
            String []childFiles = curDirectory.list();
            if(childFiles != null)
            {
                if(startFileIndex < childFiles.length)
                {                    
                    for(int i = startFileIndex; i < childFiles.length; i++)
                    {
                        if(nMaxNoOfFiles == actualNoOfFiles) break;
                        actualNoOfFiles++;
                        str_ListOfFiles.append(childFiles[i]).append(" ");
                    }
                }
                else
                {
                    str_ListOfFiles.append("");
                    actualNoOfFiles = 0;
                }
            }
            else
            {
                str_ListOfFiles.append("");
                actualNoOfFiles = 0;
            }
            
            //Send List reply
            str_To_Send.append("Rsp list").append(" ").append(i_Req_No).append(" ");
            str_To_Send.append("SUCCESS").append(" ").append(startFileIndex).append(" ");
            str_To_Send.append(actualNoOfFiles).append(" ").append(str_ListOfFiles.toString());
            responseWriter.println(str_To_Send.toString());
            responseWriter.flush();

            log.WriteToLogFile(str_To_Send.toString());

            StringBuilder doneMsg = new StringBuilder();
            doneMsg.setLength(0);
            doneMsg.append("\nDirectory List done...\n");
            System.out.println(doneMsg.toString());
            logProcesses.WriteToLogFile(doneMsg.toString());

        } catch(FileNotFoundException fe) {
            isError = true; enumerrCode = ERRORCODE.FILE_IO_ERR;
        } catch (IOException ex) {
            enumerrCode = ERRORCODE.SOCKET_IO_ERR;
            isError = true;
        } catch (Exception ex) {
            enumerrCode = ERRORCODE.UNRECOGNIZED_REQ_ERR;
            isError = true;
        }
        finally
        {
            //If error thrown
            if(isError)
            {
                //Send Hello reply with Failure code
                SendErrorReply(str_requestLine,enumerrCode);
            }
        }
    }

    public void HandleAbort(String str_requestLine)
    {
        StringBuffer str_To_Send = null;
        StringBuffer str_Received = null;
        PrintWriter responseWriter = null;
        boolean isError = false;
        String str_fileName = null;
        String searchKey = null;
        String str_Operation = null;
        ERRORCODE enumerrCode = ERRORCODE.NO_ERROR;
        int ReqNo, FileNo;
        int i_priority = 0;
        try {
            str_To_Send = new StringBuffer();
            str_Received = new StringBuffer();

            responseWriter = new PrintWriter(clientSock.getOutputStream(), true);

            str_Received.append(str_requestLine);
            
            log.WriteToLogFile(str_Received.toString());

            String arr_str_Split[] = str_Received.toString().split(" ");
            i_Req_No = Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client
            ReqNo =  Integer.parseInt(arr_str_Split[2]);  //Get the request number sent by client
            i_File_No = Integer.parseInt(arr_str_Split[3]);  //Get file number sentby client
            FileNo = Integer.parseInt(arr_str_Split[3]);  //Get file number sentby client
            searchKey = Integer.toString(ReqNo) + "#" + Integer.toString(FileNo);

            synchronized(hashOpenList)
            {
                if(hashOpenList.containsKey(searchKey)) // File is opened for get or put request
                {
                    //str_fileName = hashOpenList.get(searchKey);
                    str_fileName = GetOpenFileName(searchKey);
                    str_Operation = GetOperationOnFile(searchKey);
                    i_priority = ((OpenFiles)hashOpenList.get(searchKey)).i_Priority;
                    
                    //Check if operation is get or put and accordingly call logic
                    if(str_Operation.equalsIgnoreCase("get")) //Abort on get
                    {
                        //For get Just send Abort reply and delete entry from list of open files
                        hashOpenList.remove(searchKey);
                        str_To_Send.setLength(0);
                        str_To_Send.append("Rsp Abort").append(" ").append(ReqNo);
                       
                    }
                    else if(str_Operation.equalsIgnoreCase("put")) //Abort on Put operation
                    {
                        //Delete the partial file on server or restore original file if it was being replaced a
                        //and then send abort reply
                        String str_OldPath = System.getProperty("user.dir") + File.separator + str_fileName + ".bak";
                        String str_NewPath = System.getProperty("user.dir") + File.separator + str_fileName ;
                        File fileBackUp = new File(str_OldPath);
                        if(fileBackUp.exists()) //It means backup was created for file before put
                        {
                            //Delete partial file From disk
                            File fDelete = new File(str_NewPath);
                            fDelete.delete();
                            //Rename backup to original Name
                            File old = new File(str_OldPath);
                            old.renameTo(new File(str_NewPath));
                        }
                        else
                        {
                            //Delete partial file from disk
                            File fDelete = new File(str_NewPath);
                            fDelete.delete();
                        }
                        hashOpenList.remove(searchKey);     //Remove from Open List
                        str_To_Send.setLength(0);
                        str_To_Send.append("Rsp Abort").append(" ").append(ReqNo);
                    }
                }
            }
                               
            responseWriter.println(str_To_Send.toString());
            responseWriter.flush();

            //If abort get request decrease reader count
            if(str_Operation.equalsIgnoreCase("get"))
            {
                synchronized(hshQueueTable)
                {
                    QueueTableValue qtval = null;
                    if(hshQueueTable.containsKey(str_fileName))
                    {
                        qtval = (QueueTableValue)hshQueueTable.get(str_fileName);
                        if(qtval.iReaderCount!=0)
                            qtval.iReaderCount--;
                    }
                }
            }

            StringBuilder doneMsg = new StringBuilder();
            doneMsg.setLength(0);
            doneMsg.append("\nTime: ").append(Calendar.getInstance().getTime());
            doneMsg.append(" --->Abort for ").append(str_Operation).append(" with priority: ").append(i_priority).append(" done...\n");
            System.out.println(doneMsg.toString());
            logProcesses.WriteToLogFile(doneMsg.toString());

            //Awake after abort
            AwakeProcess(str_fileName); //Awake Processes waiting on File queue
            
            log.WriteToLogFile(str_To_Send.toString());
            
        } catch(FileNotFoundException fe) {
            isError = true; enumerrCode = ERRORCODE.FILE_IO_ERR;
        } catch (IOException ex) {
            enumerrCode = ERRORCODE.SOCKET_IO_ERR;
            isError = true;
        } catch (Exception ex) {
            enumerrCode = ERRORCODE.UNRECOGNIZED_REQ_ERR;
            isError = true;
        }
        finally
        {
            //If error thrown
            if(isError)
            {
                //Send Hello reply with Failure code
                SendErrorReply(str_requestLine,enumerrCode);
            }
        }
    }

    //Encode Byte Array To String
    public String encodeToString(byte [] byteArr)
        {
            StringBuffer strBuf = new StringBuffer();
            strBuf.setLength(0);
            int i = 0;
            for(i = 0; i < byteArr.length; i++)
            {
                boolean [] bits = new boolean[8];
                bits = ByteTobits(byteArr[i]);
                for(int j = 0; j < 8; j++)
                {
                    if(bits[j]==true)
                    {
                        strBuf.append("1");
                    }
                    else if(bits[j]==false)
                    {
                        strBuf.append("0");
                    }
                }
            }
            return strBuf.toString();
        }

        //Decode String to byte Array
        public byte[] decodeFromString(String strOfBits, int chunkSize)
        {
            int i = 0;
            int j = 0;
            int k = 0;
            byte []arrByteRet = new byte[chunkSize];
            char zero = '0';
            char one = '1';
            boolean []bits = new boolean[8];
            try
            {                
                for(i = 0; i < strOfBits.length(); i++)
                {
                    if(((i+1)%8)==0)
                    {
                        if(strOfBits.charAt(i) == zero)
                        {
                            bits[j++] = false;
                        }
                        else if(strOfBits.charAt(i) == one)
                        {
                            bits[j++] = true;
                        }
                        j = 0;
                        byte b = bitsToByte(bits);
                        arrByteRet[k++] = b;
                        if(k==chunkSize) break;
                    }
                    else
                    {
                        if(strOfBits.charAt(i) == zero)
                        {
                            bits[j++] = false;
                        }
                        else if(strOfBits.charAt(i) == one)
                        {
                            bits[j++] = true;
                        }
                    }
                }
            }
            catch(Exception ex)
            {
                System.err.println("Error occured decoding: " + ex.getMessage());
            }
            return arrByteRet;
        }

        public boolean[] ByteTobits(byte b)
        {
            boolean [] bits = new boolean[8];
            for (int i = 0; i < bits.length; i++)
            {
                bits[i] = ((b & (1 << i)) != 0);
            }
            return bits;
        }

        public byte bitsToByte(boolean[] bits)
        {
                    return bitsToByte(bits, 0);
        }

        public byte bitsToByte(boolean[] bits, int offset)
        {
            int value = 0;
            for (int i = 0; i < 8; i++)
            {
                if(bits[i] == true)
                {
                        value = value | (1 << i);
                }
            }
            return (byte)value;
        }
}
