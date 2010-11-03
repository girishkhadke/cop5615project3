import java.io.*;
import java.net.*;
import java.util.*;
/**
 *
 * Created By: Girish Khadke
 * Date: 13 Sept 2010
 */

public class client
{
    private Socket clientSock = null;
    private ConfigFileParser cfp = null;
    private LogWriter log = null;

    private Random randGenerate= null;
    private Integer i_Req_No;
    private Integer i_File_No;

    //List of open files
    private static Hashtable<String,OpenFiles> hashOpenList = null;
    
    client()
    {
        try
        {
            cfp = new ConfigFileParser(System.getProperty("user.dir") + File.separator + "config.ini");
            randGenerate = new Random();
            java.net.InetAddress localMachine = java.net.InetAddress.getLocalHost();
            cfp.set_ClientName(localMachine.getHostName());
            GetRandNumbers();
            String client_Name = cfp.get_ClientName();
            log = new LogWriter(System.getProperty("user.dir") + File.separator + client_Name + ".log");
            hashOpenList = new Hashtable<String, OpenFiles>();
        } catch (Exception ex) {
            System.err.println("Exception: " + ex.getMessage());
            System.exit(0);
        }
    }

    private void AddToHashTableOpenFile(String str_Key, String str_FileName, String str_Operation, int iPriority)
    {
        OpenFiles of = new OpenFiles(str_FileName, str_Operation, iPriority);
        hashOpenList.put(str_Key, of);
    }

    private String GetOpenFileName(String str_searchKey)
    {
        OpenFiles of = new OpenFiles();
        of = hashOpenList.get(str_searchKey);
        return of.str_OpenFileName;
    }

    private String GetOperationOnFile(String str_searchKey)
    {
        OpenFiles of = new OpenFiles();
        of = hashOpenList.get(str_searchKey);
        return of.str_Operation;
    }
    
    private void GetRandNumbers()
    {
        this.i_Req_No = randGenerate.nextInt(100000);
        this.i_File_No = randGenerate.nextInt(100000);
    }

    private boolean SendHello()
    {
        StringBuffer str_To_Send = null;
        StringBuffer str_To_Received = null;
        BufferedReader responseReader = null;
        PrintWriter requestWriter = null;
        String readLine = null;
        boolean blnIsFailure = false;
        try
        {
            str_To_Send = new StringBuffer();
            str_To_Received = new StringBuffer();
            //Initialize Streams
            responseReader = new BufferedReader(new InputStreamReader(clientSock.getInputStream()));
            requestWriter = new PrintWriter(clientSock.getOutputStream(),true);
            
            //Say hello request
            str_To_Send.append("Req Hello").append(" ").append(i_Req_No);
            requestWriter.println(str_To_Send.toString());
            requestWriter.flush();

            //Write Log
            log.WriteToLogFile(str_To_Send.toString());

            //Read Response
            readLine = responseReader.readLine();            
            str_To_Received.append(readLine);

            //Write Log
            log.WriteToLogFile(str_To_Received.toString());

            //HANDLE FAILURE (No need Just log failure)
            String arr_str_split[] = str_To_Received.toString().split(" ");
            if(arr_str_split[0].equalsIgnoreCase("Rsp") && arr_str_split[1].equalsIgnoreCase("Hello"))
            {
                if(arr_str_split[2].equalsIgnoreCase(Integer.toString(i_Req_No)))
                {
                    if(arr_str_split[3].equalsIgnoreCase("FAILURE"))
                    {
                        System.err.println("Hello request failed!! Server may be too busy!!");
                        blnIsFailure = true;
                    }
                }
            }
            
        }
        catch (UnknownHostException e)
        {
            System.err.println("Unknown host!!!" + e.getMessage());
            System.exit(0);
        } catch (IOException e)
        {
            System.err.println("Couldn't get I/O for the connection to host");
            System.exit(0);
        } catch(Exception ex)
        {
            System.err.println("Exception caught!!");
            System.exit(0);
        }
        finally
        {
            //Garbage collection 
            str_To_Send = str_To_Received = null;
            readLine = null;
            return blnIsFailure;
        }
    }

    private void FilePut(String str_file_name, int iPriority)
    {
        String str_Path = null;
        StringBuffer str_To_Send = null;
        StringBuffer str_To_Received = null;
        FileInputStream fstream = null;
        BufferedReader responseReader = null;
        PrintWriter requestWriter = null;
        RandomAccessFile randFile = null;
        String readLine = null;
        int startByte = 0;
        String isLast = "NOTLAST";
        try
        {
            str_To_Send = new StringBuffer();
            str_To_Received = new StringBuffer();
            str_Path = System.getProperty("user.dir") + File.separator + str_file_name;            

            if(!(new File(str_Path).exists()))
            {
                System.err.println("File does not exists on Client!!! Please give another file name!!!");
                return;
            }

            //Initialize Streams
            fstream = new FileInputStream(str_Path);  //Read File
            responseReader = new BufferedReader(new InputStreamReader(clientSock.getInputStream()));
            requestWriter = new PrintWriter(clientSock.getOutputStream(),true);

            //while loop in hello blocks and hence its terminated when we get successful response
            if(SendHello()) { return; }    //If Send Hello fails , sequence ends

            str_To_Send.append("Req put");
            str_To_Send.append(" ");
            str_To_Send.append(i_Req_No);
            str_To_Send.append(" ");
            //str_To_Send.append(str_file_name);
            //File will be saved as <ClientName>@<FileName> on server
            str_To_Send.append(cfp.get_ClientName()).append("@").append(str_file_name);
            str_To_Send.append(" ").append(iPriority);     
            
            requestWriter.println(str_To_Send.toString());
            requestWriter.flush();
            
            //Log request put
            log.WriteToLogFile(str_To_Send.toString());

            //Read Response put
            readLine = responseReader.readLine();
            str_To_Received.append(readLine);
            //Log response put
            log.WriteToLogFile(str_To_Received.toString());

            //Now send file in chunks if server ready
            String arr_str_split[] = str_To_Received.toString().split(" ");
            if(arr_str_split[0].equalsIgnoreCase("Rsp") && arr_str_split[1].equalsIgnoreCase("put"))
            {
                if(arr_str_split[2].equalsIgnoreCase(Integer.toString(i_Req_No)))
                {

                    if(arr_str_split[4].equalsIgnoreCase("WAIT"))
                    {
                        //Read Get response till ready or failure
                        readLine = responseReader.readLine();
                        str_To_Received.setLength(0);
                        str_To_Received.append(readLine);
                        arr_str_split = str_To_Received.toString().split(" ");
                        //Log whatever ready or failure reply
                        log.WriteToLogFile(str_To_Received.toString());
                    }
                    
                    //Same file number and server is ready
                    if(arr_str_split[4].equalsIgnoreCase("READY"))
                    {
                        i_File_No = Integer.parseInt(arr_str_split[5]);   //Server's chosen file number                                                
                        //Send pushes
                        while(true)
                        {
                            int noOfBytesRead = 0;
                            byte readBytes[] = new byte[cfp.get_ChunkSize()];
                            
                            randFile = new RandomAccessFile(str_Path, "r");
                            randFile.seek(startByte);
                            noOfBytesRead = randFile.read(readBytes);
                            byte []readNewBytes;
                            
                            if((startByte + cfp.get_ChunkSize()) > randFile.length())
                            {
                                readNewBytes = new byte[noOfBytesRead];
                                System.arraycopy(readBytes, 0, readNewBytes, 0, noOfBytesRead);
                                readBytes = readNewBytes;
                                isLast = "LAST";
                            }
                            else
                            {
                                isLast = "NOTLAST";
                            }

//                            if(noOfBytesRead==-1)
//                            {
//                                break;
//                            }

                            StringBuffer dataToSend = new StringBuffer();
                            dataToSend.setLength(0);
                            dataToSend.append(encodeToString(readBytes));

//                            if(noOfBytesRead < cfp.get_ChunkSize())
//                                isLast = "LAST";

                            str_To_Send.setLength(0);
                            str_To_Send.append("Req push").append(" ").append(i_Req_No).append(" ");
                            str_To_Send.append(i_File_No).append(" ").append(isLast);
                            str_To_Send.append(" ").append(startByte).append(" ").append(noOfBytesRead);
                            str_To_Send.append(" ").append(dataToSend.toString());

                            StringBuffer printBuf = new StringBuffer();
                            printBuf.setLength(0);
                            printBuf.append("Req push").append(" ").append(i_Req_No).append(" ");
                            printBuf.append(i_File_No).append(" ").append(isLast);
                            printBuf.append(" ").append(startByte).append(" ").append(noOfBytesRead);
                            printBuf.append(" <").append(readBytes.length).append("> ");
                            //Log Push request
                            log.WriteToLogFile(printBuf.toString());
                            
                            //Sent the request push
                            requestWriter.println(str_To_Send.toString());
                            requestWriter.flush();

                            //Read the response
                            readLine = responseReader.readLine();
                            str_To_Received.setLength(0);
                            str_To_Received.append(readLine);

                            //Log Push reply
                            log.WriteToLogFile(str_To_Received.toString());

                            //Handle PUSH FAILURE (Just log failure and if failure from server...return)
                            String []str_Split_Response = str_To_Received.toString().split(" ");
                            if(str_Split_Response[0].equalsIgnoreCase("Rsp") && str_Split_Response[1].equalsIgnoreCase("Push"))
                            {
                                if(str_Split_Response[2].equalsIgnoreCase(Integer.toString(i_Req_No)))
                                {
                                    if(str_Split_Response[3].equalsIgnoreCase("FAILURE"))
                                    {
                                        System.err.println("Error occured during push!! Retry File put again!!");
                                        randFile.close();
                                        return;
                                    }
                                }
                            }                          

                            //Break if LAST chunk
                            if(isLast.equalsIgnoreCase("LAST"))
                            {
                                System.out.println("File transferred on server and it is saved as <ClientName>@<FileName> on server!!");
                                break;
                            }
                            
                            startByte = startByte + noOfBytesRead;
                            randFile.close();
                        }
                    }
                    else if(arr_str_split[4].equalsIgnoreCase("FAILURE"))
                    {
                        //FAILURE FOR PUT
                        //Just need to log the Message which is already done

                        System.err.println("Error Occured for Put Request!! Server might not be ready!! Try again!!");                                                
                        return;
                    }
                }
            }
            
        } catch(FileNotFoundException fe)
        {
            System.err.println("File not found : " + fe.getMessage());
            System.exit(0);
        }
        catch (UnknownHostException e)
        {
            System.err.println("Unknown host : " + e.getMessage());
            System.exit(0);
        } catch (IOException e)
        {
            System.err.println("Couldn't get I/O for the connection to host : " + e.getMessage());
            System.exit(0);
        } catch(Exception ex)
        {
            System.err.println("Exception caught : " + ex.getMessage());
            System.exit(0);
        }
        finally
        {
            try {
                if(fstream!=null) 
                    fstream.close();
            } catch (IOException ex) {
            }
        }
    }

    private void FileaPut(String str_file_name, int noOfChunks, int iPriority)
    {
        String str_Path = null;
        StringBuffer str_To_Send = null;
        StringBuffer str_To_Received = null;
        StringBuffer str_AbortReplyReceived = null;
        FileInputStream fstream = null;
        BufferedReader responseReader = null;
        PrintWriter requestWriter = null;
        RandomAccessFile randFile = null;
        int abortCounter = 0;
        String readLine = null;
        int startByte = 0;
        String isLast = "NOTLAST";
        try
        {
            str_To_Send = new StringBuffer();
            str_To_Received = new StringBuffer();
            str_AbortReplyReceived = new StringBuffer();
            str_Path = System.getProperty("user.dir") + File.separator + str_file_name;

            if(!(new File(str_Path).exists()))
            {
                System.err.println("File does not exists on Client!!! Please give another file name!!!");
                return;
            }

            //Initialize Streams
            fstream = new FileInputStream(str_Path);  //Read File
            responseReader = new BufferedReader(new InputStreamReader(clientSock.getInputStream()));
            requestWriter = new PrintWriter(clientSock.getOutputStream(),true);

            //while loop in hello blocks and hence its terminated when we get successful response
            if(SendHello()) { return; }    //If Send Hello fails , sequence ends

            str_To_Send.append("Req put");
            str_To_Send.append(" ");
            str_To_Send.append(i_Req_No);
            str_To_Send.append(" ");
            //str_To_Send.append(str_file_name);
            //File will be saved as <ClientName>@<FileName> on server
            str_To_Send.append(cfp.get_ClientName()).append("@").append(str_file_name);
            str_To_Send.append(" ").append(iPriority);     

            requestWriter.println(str_To_Send.toString());
            requestWriter.flush();

            //Log request put
            log.WriteToLogFile(str_To_Send.toString());

            //Read Response put
            readLine = responseReader.readLine();
            str_To_Received.append(readLine);
            //Log response put
            log.WriteToLogFile(str_To_Received.toString());

            //Now send file in chunks if server ready
            String arr_str_split[] = str_To_Received.toString().split(" ");
            if(arr_str_split[0].equalsIgnoreCase("Rsp") && arr_str_split[1].equalsIgnoreCase("put"))
            {
                if(arr_str_split[2].equalsIgnoreCase(Integer.toString(i_Req_No)))
                {

                    if(arr_str_split[4].equalsIgnoreCase("WAIT"))
                    {
                        //Read Get response till ready or failure
                        readLine = responseReader.readLine();
                        str_To_Received.setLength(0);
                        str_To_Received.append(readLine);
                        arr_str_split = str_To_Received.toString().split(" ");
                        //Log whatever ready or failure reply
                        log.WriteToLogFile(str_To_Received.toString());
                    }
                    
                    //Same file number and server is ready
                    if(arr_str_split[4].equalsIgnoreCase("READY"))
                    {
                        int iFileNo = Integer.parseInt(arr_str_split[5]);   //Server's chosen file number
                        //Send pushes
                        while(true)
                        {
                            if(abortCounter >= noOfChunks)
                            {
                                //Send abort message and Break while loop
                                str_To_Send.setLength(0);
                                str_To_Send.append("Req Abort").append(" ").append(i_Req_No).append(" ");
                                str_To_Send.append(iFileNo);

                                //Sent the request abort
                                requestWriter.println(str_To_Send.toString());
                                requestWriter.flush();

                                //Log abort request
                                log.WriteToLogFile(str_To_Send.toString());

                                readLine = responseReader.readLine();
                                str_AbortReplyReceived.setLength(0);
                                str_AbortReplyReceived.append(readLine);

                                //Log abort reply
                                log.WriteToLogFile(str_AbortReplyReceived.toString());

                                break;
                            }

                            int noOfBytesRead = 0;
                            byte readBytes[] = new byte[cfp.get_ChunkSize()];

                            randFile = new RandomAccessFile(str_Path, "r");
                            randFile.seek(startByte);
                            noOfBytesRead = randFile.read(readBytes);
                            byte []readNewBytes;

                            if((startByte + cfp.get_ChunkSize()) > randFile.length())
                            {
                                readNewBytes = new byte[noOfBytesRead];
                                System.arraycopy(readBytes, 0, readNewBytes, 0, noOfBytesRead);
                                readBytes = readNewBytes;
                                isLast = "LAST";
                            }
                            else
                            {
                                isLast = "NOTLAST";
                            }

//                            if(noOfBytesRead==-1)
//                            {
//                                break;
//                            }
                            StringBuffer dataToSend = new StringBuffer();
                            dataToSend.setLength(0);
                            dataToSend.append(encodeToString(readBytes));

//                            if(noOfBytesRead < cfp.get_ChunkSize())
//                                isLast = "LAST";

                            str_To_Send.setLength(0);
                            str_To_Send.append("Req push").append(" ").append(i_Req_No).append(" ");
                            str_To_Send.append(iFileNo).append(" ").append(isLast);
                            str_To_Send.append(" ").append(startByte).append(" ").append(noOfBytesRead);
                            str_To_Send.append(" ").append(dataToSend.toString());

                            StringBuffer printBuf = new StringBuffer();
                            printBuf.setLength(0);
                            printBuf.append("Req push").append(" ").append(i_Req_No).append(" ");
                            printBuf.append(iFileNo).append(" ").append(isLast);
                            printBuf.append(" ").append(startByte).append(" ").append(noOfBytesRead);
                            printBuf.append(" <").append(readBytes.length).append("> ");
                            //Log Push request
                            log.WriteToLogFile(printBuf.toString());

                            //Sent the request push
                            requestWriter.println(str_To_Send.toString());
                            requestWriter.flush();
                            abortCounter++;

                            //Read the response
                            readLine = responseReader.readLine();
                            str_To_Received.setLength(0);
                            str_To_Received.append(readLine);
                            
                            //Log Push reply
                            log.WriteToLogFile(str_To_Received.toString());

                            //Handle PUSH FAILURE (Just log failure and if failure from server...return)
                            String []str_Split_Response = str_To_Received.toString().split(" ");
                            if(str_Split_Response[0].equalsIgnoreCase("Rsp") && str_Split_Response[1].equalsIgnoreCase("Push"))
                            {
                                if(str_Split_Response[2].equalsIgnoreCase(Integer.toString(i_Req_No)))
                                {
                                    if(str_Split_Response[3].equalsIgnoreCase("FAILURE"))
                                    {
                                        System.err.println("Error occured during push!! Retry File put again!!");
                                        randFile.close();
                                        return;
                                    }
                                }
                            }

                            //Break if LAST chunk
                            if(isLast.equalsIgnoreCase("LAST"))
                            {
                                break;
                            }

                            startByte = startByte + noOfBytesRead;
                            randFile.close();
                        }
                    }
                    else if(arr_str_split[4].equalsIgnoreCase("FAILURE"))
                    {
                        //FAILURE FOR PUT
                        //Just need to log the Message which is already done

                        System.err.println("Error Occured for Put Request!! Server might not be ready!! Try again!!");
                        return;
                    }
                }
            }

        } catch(FileNotFoundException fe)
        {
            System.err.println("File not found : " + fe.getMessage());
            System.exit(0);
        }
        catch (UnknownHostException e)
        {
            System.err.println("Unknown host : " + e.getMessage());
            System.exit(0);
        } catch (IOException e)
        {
            System.err.println("Couldn't get I/O for the connection to host : " + e.getMessage());
            System.exit(0);
        } catch(Exception ex)
        {
            System.err.println("Exception caught : " + ex.getMessage());
            System.exit(0);
        }
        finally
        {
            try {
                if(fstream!=null)
                    fstream.close();
            } catch (IOException ex) {
            }
        }
    }
   

     private void FileGet(String str_file_name, int iPriority)
     {
        String str_FileSavePath = null;
        StringBuffer str_To_Send = null;
        StringBuffer str_To_Received = null;
        StringBuffer str_pullReplyReceived = null;
        FileOutputStream fstream = null;
        BufferedReader responseReader = null;
        PrintWriter requestWriter = null;
        String isLast = "NOTLAST";
        boolean isSameFileExists = false;
        String searchKey = null;
        StringBuffer printBuf = null;
        String readLine = null;
        try
        {
            str_To_Send = new StringBuffer();
            str_To_Received = new StringBuffer();
            str_pullReplyReceived = new StringBuffer();
            str_FileSavePath = System.getProperty("user.dir") + File.separator + cfp.get_ClientName() + "@" + str_file_name;

            //Initialize Streams            
            responseReader = new BufferedReader(new InputStreamReader(clientSock.getInputStream()));
            requestWriter = new PrintWriter(clientSock.getOutputStream(),true);

            //while loop in hello blocks and hence its terminated when we get successful response
            if(SendHello()) { return; }    //If Send Hello fails , sequence ends

            str_To_Send.setLength(0);
            str_To_Send.append("Req get");
            str_To_Send.append(" ");
            str_To_Send.append(i_Req_No);
            str_To_Send.append(" ");
            str_To_Send.append(str_file_name);
            str_To_Send.append(" ").append(iPriority);     

            requestWriter.println(str_To_Send.toString());
            requestWriter.flush();

            //Log Get request
            log.WriteToLogFile(str_To_Send.toString());

            //Read Get response
            readLine = responseReader.readLine();
            str_To_Received.append(readLine);

            //Log Get response
            log.WriteToLogFile(str_To_Received.toString());            
            
            //Now Get file in chunks from server if server ready
            String arr_str_split[] = str_To_Received.toString().split(" ");
            if(arr_str_split[0].equalsIgnoreCase("Rsp") && arr_str_split[1].equalsIgnoreCase("get"))
            {
                if(arr_str_split[2].equalsIgnoreCase(Integer.toString(i_Req_No)) && arr_str_split[3].equalsIgnoreCase(str_file_name))
                {

                    if(arr_str_split[4].equalsIgnoreCase("WAIT"))
                    {
                        //Read Get response till ready or failure 
                        readLine = responseReader.readLine();
                        str_To_Received.setLength(0);
                        str_To_Received.append(readLine);
                        arr_str_split = str_To_Received.toString().split(" ");
                        //Log whatever ready or failure reply
                        log.WriteToLogFile(str_To_Received.toString());
                    }

                    //Same file number and server is ready
                    if(arr_str_split[4].equalsIgnoreCase("READY"))
                    {
                        //If server is ready to give file but same file name exists on client                        
                        File fCheck = new File(str_FileSavePath);
                        if(fCheck.exists())
                        {
                            isSameFileExists = true;
                        }

                        if(isSameFileExists){
                            //Same file exists means Rename existing file to a new extension .bak
                            //In case of error while writing data for new file , revert back old file by rename
                            File old = new File(str_FileSavePath);
                            old.renameTo(new File(str_FileSavePath + ".bak"));  //Old file is renamed
                            System.out.println("File already exists by same name: Old file is renamed with .bak extension");
                        }

                        i_File_No = Integer.parseInt(arr_str_split[5]);   //Server's chosen file number
                        searchKey = Integer.toString(i_Req_No) + "#" + Integer.toString(i_File_No);
                        //hashOpenList.put(searchKey, str_file_name); //List of unfinished open files on client
                        AddToHashTableOpenFile(searchKey, cfp.get_ClientName() + "@" + str_file_name, "get", iPriority); //List of unfinished open files on client (get)
                        
                        int startByte = 0;
                        //Open File on client (Append Mode)
                        fstream = new FileOutputStream(str_FileSavePath,true);
                        //Send Pulls
                        while(true)
                        {
                            //Break if received last chunk
                            if(isLast.equalsIgnoreCase("LAST"))
                            {                                
                                hashOpenList.remove(searchKey); //Once last data chunk of File is completely written, remove entry
                                if(isSameFileExists)
                                {
                                    //Now Physically delete *.bak file since new file written properly
                                    File fToDelete = new File(str_FileSavePath + ".bak");   //Check if .bak file exists on client
                                    if(fToDelete.exists())
                                    {
                                        fToDelete.delete(); 
                                    }                                    
                                }                                
                                System.out.println("File downloaded from server and saved as <ClientName>@<FileName> on client!!");
                                break;
                            }

                            int nLengthToReceive = cfp.get_ChunkSize();
                            str_To_Send.setLength(0);
                            str_To_Send.append("Req pull").append(" ").append(i_Req_No).append(" ");
                            str_To_Send.append(i_File_No).append(" ");
                            str_To_Send.append(startByte).append(" ").append(nLengthToReceive);

                            //Send Pull request
                            requestWriter.println(str_To_Send.toString());   //Send Pull request
                            requestWriter.flush();
                            //Log Pull Request
                            log.WriteToLogFile(str_To_Send.toString());

                            //Read Pull response
                            readLine = responseReader.readLine();
                            str_pullReplyReceived.setLength(0);
                            str_pullReplyReceived.append(readLine);

                            String arr_To_Split[] = str_pullReplyReceived.toString().split(" ");
                            //If Pull is successfull
                            if(arr_To_Split[0].equalsIgnoreCase("Rsp") && arr_To_Split[1].equalsIgnoreCase("pull") && arr_To_Split[2].equalsIgnoreCase(Integer.toString(i_Req_No)))
                            {
                                if(arr_To_Split[3].equalsIgnoreCase("SUCCESS"))
                                {
                                    int req_no = Integer.parseInt(arr_To_Split[2]);
                                    isLast = arr_To_Split[4].toString();                                    
                                    int noByteReceived = Integer.parseInt(arr_To_Split[6]);
                                    
                                    //Read bytes from data field                                    
                                    byte readBytes[] = decodeFromString(arr_To_Split[7],noByteReceived);
                                    int iData = readBytes.length;

                                    printBuf = new StringBuffer();
                                    printBuf.append(arr_To_Split[0]).append(" ").append(arr_To_Split[1]).append(" ");
                                    printBuf.append(req_no).append(" ").append(arr_To_Split[3]).append(" ");
                                    printBuf.append(arr_To_Split[4]).append(" ").append(arr_To_Split[5]).append(" ");
                                    printBuf.append(noByteReceived).append(" ").append("<").append(iData).append(">");

                                    //Log Pull response
                                    log.WriteToLogFile(printBuf.toString());                                    
                                    
                                    fstream.write(readBytes);                                    

                                    if(noByteReceived < cfp.get_ChunkSize())
                                        break;

                                    startByte = startByte + noByteReceived ;
                                }
                                else if(arr_To_Split[3].equalsIgnoreCase("FAILURE"))
                                {
                                    //HANDLE FAILURE FOR PULL
                                    StringBuffer strBuf = new StringBuffer();
                                    strBuf.append(arr_To_Split[0]).append(" ").append(arr_To_Split[1]).append(" ");
                                    strBuf.append(arr_To_Split[2]).append(" ").append(arr_To_Split[3]).append(" ");
                                    strBuf.append(arr_To_Split[4]);

                                    //Log Pull response
                                    log.WriteToLogFile(strBuf.toString());
                                    
                                    hashOpenList.remove(searchKey); //Remove from list of unfinished open files
                                    if(isSameFileExists)
                                    {
                                        //In case of error on single pull revert back original file
                                        fstream.close();    //Close stream needed;
                                        File old = new File(str_FileSavePath + ".bak");
                                        old.renameTo(new File(str_FileSavePath));  //Old file is renamed
                                        System.err.println("Old file on client with same name restored since client got error while getting chunks from server!! Retry get again!!");
                                    }                                    
                                    System.err.println("Error while getting chunks from server!! Please retry get again");
                                    return;
                                }
                            }                            
                        }   //While loop
                        fstream.close();
                    }                    
                    else if(arr_str_split[4].equalsIgnoreCase("FAILURE"))
                    {
                        //Failure means file does not exist on server side
                        //FAILURE FOR GET (Nothing else needed here)
                        System.err.println("Can not get File from server!! File may not exist at server!!!");
                        return;
                    }
                }
            }

        } catch(FileNotFoundException fe)
        {
            System.err.println("File not found : " + fe.getMessage());
            System.exit(0);
        }
        catch (UnknownHostException e)
        {
            System.err.println("Unknown host : " + e.getMessage());
            System.exit(0);
        } catch (IOException e)
        {
            System.err.println("Couldn't get I/O for the connection to host : " + e.getMessage());
            System.exit(0);
        } catch(Exception ex)
        {
            System.err.println("Exception caught : " + ex.getMessage());
            System.exit(0);
        }
        finally {
            try {
                if(fstream!=null) 
                    fstream.close();
            } catch (IOException ex) {                
            }            
        }
    }

    private void FileaGet(String str_file_name, int noOfChunks, int iPriority)
     {
        String str_FileSavePath = null;
        StringBuffer str_To_Send = null;
        StringBuffer str_To_Received = null;
        StringBuffer str_pullReplyReceived = null;
        StringBuffer str_AbortReplyReceived = null;
        FileOutputStream fstream = null;
        BufferedReader responseReader = null;
        PrintWriter requestWriter = null;
        String isLast = "NOTLAST";
        boolean isSameFileExists = false;
        String searchKey = null;
        StringBuffer printBuf = null;
        String readLine = null;
        int abortCounter = 0;
        try
        {
            str_To_Send = new StringBuffer();
            str_To_Received = new StringBuffer();
            str_pullReplyReceived = new StringBuffer();
            str_AbortReplyReceived = new StringBuffer();            
            str_FileSavePath = System.getProperty("user.dir") + File.separator + cfp.get_ClientName() + "@" + str_file_name;

            //Initialize Streams
            responseReader = new BufferedReader(new InputStreamReader(clientSock.getInputStream()));
            requestWriter = new PrintWriter(clientSock.getOutputStream(),true);

            //while loop in hello blocks and hence its terminated when we get successful response
            if(SendHello()) { return; }    //If Send Hello fails , sequence ends

            str_To_Send.setLength(0);
            str_To_Send.append("Req get");
            str_To_Send.append(" ");
            str_To_Send.append(i_Req_No);
            str_To_Send.append(" ");
            str_To_Send.append(str_file_name);
            str_To_Send.append(" ").append(iPriority);     
            
            requestWriter.println(str_To_Send.toString());
            requestWriter.flush();

            //Log Get request
            log.WriteToLogFile(str_To_Send.toString());

            //Read Get response
            readLine = responseReader.readLine();
            str_To_Received.append(readLine);

            //Log Get response
            log.WriteToLogFile(str_To_Received.toString());

            //Now Get file in chunks from server if server ready
            String arr_str_split[] = str_To_Received.toString().split(" ");
            if(arr_str_split[0].equalsIgnoreCase("Rsp") && arr_str_split[1].equalsIgnoreCase("get"))
            {
                if(arr_str_split[2].equalsIgnoreCase(Integer.toString(i_Req_No)))
                {

                    if(arr_str_split[4].equalsIgnoreCase("WAIT"))
                    {
                        //Read Get response till ready or failure
                        readLine = responseReader.readLine();
                        str_To_Received.setLength(0);
                        str_To_Received.append(readLine);
                        arr_str_split = str_To_Received.toString().split(" ");
                        //Log whatever ready or failure reply
                        log.WriteToLogFile(str_To_Received.toString());
                    }

                    //Same file number and server is ready
                    if(arr_str_split[4].equalsIgnoreCase("READY"))
                    {
                        //If server is ready to give file but same file name exists on client
                        File fCheck = new File(str_FileSavePath);
                        if(fCheck.exists())
                        {
                            isSameFileExists = true;
                        }

                        if(isSameFileExists){
                            //Same file exists means Rename existing file to a new extension .bak
                            //In case of error while writing data for new file , revert back old file by rename
                            File old = new File(str_FileSavePath);
                            old.renameTo(new File(str_FileSavePath + ".bak"));  //Old file is renamed
                            System.out.println("File already exists by same name: Old file is renamed with .bak extension");
                        }

                        i_File_No = Integer.parseInt(arr_str_split[5]);   //Server's chosen file number
                        searchKey = Integer.toString(i_Req_No) + "#" + Integer.toString(i_File_No);
                        //hashOpenList.put(searchKey, str_file_name); //List of unfinished open files on client
                        AddToHashTableOpenFile(searchKey, cfp.get_ClientName() + "@" + str_file_name, "get",iPriority);

                        int startByte = 0;
                        //Open File on client (Append Mode)
                        fstream = new FileOutputStream(str_FileSavePath,true);
                        //Send Pulls
                        while(true)
                        {
                            //send abort and break while loop
                            if(abortCounter >= noOfChunks)
                            {
                                str_To_Send.setLength(0);
                                str_To_Send.append("Req Abort").append(" ").append(i_Req_No).append(" ");
                                str_To_Send.append(i_File_No).append(" ");
                                //Send Abort request
                                requestWriter.println(str_To_Send.toString());   //Send Pull request
                                requestWriter.flush();
                                //Log abort request
                                log.WriteToLogFile(str_To_Send.toString());

                                //Read abort reply
                                readLine = responseReader.readLine();
                                str_AbortReplyReceived.setLength(0);
                                str_AbortReplyReceived.append(readLine);

                                //Log abort reply
                                log.WriteToLogFile(str_AbortReplyReceived.toString());

                                String [] arr_SplitAbortReply = str_AbortReplyReceived.toString().split(" ");
                                if(arr_SplitAbortReply[0].equalsIgnoreCase("Rsp") && arr_SplitAbortReply[1].equalsIgnoreCase("Abort"))
                                {
                                    if(arr_SplitAbortReply[2].equalsIgnoreCase(Integer.toString(i_Req_No)))
                                    {
                                        //Once abort is sent, remove entry from open Files list
                                        hashOpenList.remove(searchKey);                                         
                                        if(isSameFileExists)
                                        {
                                            //Remove back up file after new file get is aborted
                                            fstream.close();
                                            //Delete partial file on client
                                            File fToDelete = new File(str_FileSavePath);
                                            fToDelete.delete(); //Delete File
                                            //Rename .bak file to original name since transfer got aborted
                                            File old = new File(str_FileSavePath + ".bak");
                                            old.renameTo(new File(str_FileSavePath));  //Old file is renamed
                                            System.out.println("Original file restored on client!!");
                                        }
                                        else
                                        {
                                            fstream.close();
                                            //Delete partially saved file on client
                                            File fToDelete = new File(str_FileSavePath);
                                            fToDelete.delete(); //Delete File
                                        }
                                        break;      //Break while loop
                                    }
                                }
                            }

                            //Break if received last chunk
                            if(isLast.equalsIgnoreCase("LAST"))
                            {
                                hashOpenList.remove(searchKey); //Once last data chunk of File is completely written, remove entry
                                if(isSameFileExists)
                                {
                                    //Now Physically delete *.bak file since new file written properly
                                    File fToDelete = new File(str_FileSavePath + ".bak");   //Check if .bak file exists on client
                                    if(fToDelete.exists())
                                    {
                                        fToDelete.delete();
                                    }
                                }
                                System.out.println("File downloaded from server since last chunk is received and saved as <ClientName>@<FileName> on client!!");
                                break;
                            }
                           
                            int nLengthToReceive = cfp.get_ChunkSize();
                            str_To_Send.setLength(0);
                            str_To_Send.append("Req pull").append(" ").append(i_Req_No).append(" ");
                            str_To_Send.append(i_File_No).append(" ");
                            str_To_Send.append(startByte).append(" ").append(nLengthToReceive);

                            //Send Pull request
                            requestWriter.println(str_To_Send.toString());   //Send Pull request
                            requestWriter.flush();
                            abortCounter++;                                       //Increment AbortCounter

                            //Log Pull Request
                            log.WriteToLogFile(str_To_Send.toString());

                            //Read Pull response
                            readLine = responseReader.readLine();
                            str_pullReplyReceived.setLength(0);
                            str_pullReplyReceived.append(readLine);

                            String arr_To_Split[] = str_pullReplyReceived.toString().split(" ");
                            //If Pull is successfull
                            if(arr_To_Split[0].equalsIgnoreCase("Rsp") && arr_To_Split[1].equalsIgnoreCase("pull") && arr_To_Split[2].equalsIgnoreCase(Integer.toString(i_Req_No)))
                            {
                                if(arr_To_Split[3].equalsIgnoreCase("SUCCESS"))
                                {
                                    int req_no = Integer.parseInt(arr_To_Split[2]);
                                    isLast = arr_To_Split[4].toString();
                                    int noByteReceived = Integer.parseInt(arr_To_Split[6]);

                                    //Read bytes from data field
                                    byte readBytes[] = decodeFromString(arr_To_Split[7],noByteReceived);
                                    int iData = readBytes.length;

                                    printBuf = new StringBuffer();
                                    printBuf.append(arr_To_Split[0]).append(" ").append(arr_To_Split[1]).append(" ");
                                    printBuf.append(req_no).append(" ").append(arr_To_Split[3]).append(" ");
                                    printBuf.append(arr_To_Split[4]).append(" ").append(arr_To_Split[5]).append(" ");
                                    printBuf.append(noByteReceived).append(" ").append("<").append(iData).append(">");

                                    //Log Pull response
                                    log.WriteToLogFile(printBuf.toString());

                                    fstream.write(readBytes);

                                    startByte = startByte + noByteReceived ;
                                }
                                else if(arr_To_Split[3].equalsIgnoreCase("FAILURE"))
                                {
                                    //HANDLE FAILURE FOR PULL
                                    StringBuffer strBuf = new StringBuffer();
                                    strBuf.append(arr_To_Split[0]).append(" ").append(arr_To_Split[1]).append(" ");
                                    strBuf.append(arr_To_Split[2]).append(" ").append(arr_To_Split[3]).append(" ");
                                    strBuf.append(arr_To_Split[4]);

                                    //Log Pull response
                                    log.WriteToLogFile(strBuf.toString());

                                    hashOpenList.remove(searchKey); //Remove from list of unfinished open files
                                    if(isSameFileExists)
                                    {
                                        //In case of error on single pull revert back original file
                                        fstream.close();    //Close stream needed;
                                        File old = new File(str_FileSavePath + ".bak");
                                        old.renameTo(new File(str_FileSavePath));  //Old file is renamed
                                        System.err.println("Old file on client with same name restored since client got error while getting chunks from server!! Retry get again!!");
                                    }
                                    else
                                    {
                                        System.err.println("Error while getting chunks from server!! Please retry get again");
                                    }
                                    return;
                                }
                            }
                        }   //While loop
                        fstream.close();
                    }
                    else if(arr_str_split[4].equalsIgnoreCase("FAILURE"))
                    {
                        //Failure means file does not exist on server side
                        //FAILURE FOR GET (Nothing else needed here)
                        System.err.println("Can not get File from server!! File may not exist at server!!!");
                        return;
                    }
                }
            }

        } catch(FileNotFoundException fe)
        {
            System.err.println("File not found : " + fe.getMessage());
            System.exit(0);
        }
        catch (UnknownHostException e)
        {
            System.err.println("Unknown host : " + e.getMessage());
            System.exit(0);
        } catch (IOException e)
        {
            System.err.println("Couldn't get I/O for the connection to host : " + e.getMessage());
            System.exit(0);
        } catch(Exception ex)
        {
            System.err.println("Exception caught : " + ex.getMessage());
            System.exit(0);
        }
        finally {
            try {
                if(fstream!=null)
                    fstream.close();
            } catch (IOException ex) {
            }
        }
    }
    
    private void delete(String str_Fname, int iPriority)
    {
        StringBuffer str_To_Send = null;
        StringBuffer str_To_Received = null;
        BufferedReader responseReader = null;
        PrintWriter requestWriter = null;
        String readLine = null;
        try
        {
            str_To_Send = new StringBuffer();
            str_To_Received = new StringBuffer();
            //Initialize Streams
            responseReader = new BufferedReader(new InputStreamReader(clientSock.getInputStream()));
            requestWriter = new PrintWriter(clientSock.getOutputStream(),true);

            //Handshaking before Delete
            if(SendHello()) { return; }    //If Send Hello fails , sequence ends

            //Send delete request
            str_To_Send.append("Req delete").append(" ").append(i_Req_No).append(" ");
            str_To_Send.append(str_Fname);
            str_To_Send.append(" ").append(iPriority);     

            requestWriter.println(str_To_Send.toString());
            requestWriter.flush();

            //Log delete Request
            log.WriteToLogFile(str_To_Send.toString());

            //Read delete response
            readLine = responseReader.readLine();
            str_To_Received.setLength(0);
            str_To_Received.append(readLine);

            //Log delete Response
            log.WriteToLogFile(str_To_Received.toString());

            //Check for failure
            String arr_str_split[] = str_To_Received.toString().split(" ");            
            if(arr_str_split[0].equalsIgnoreCase("Rsp") && arr_str_split[1].equalsIgnoreCase("delete"))
            {
                if(arr_str_split[2].equalsIgnoreCase(Integer.toString(i_Req_No)))
                {

                    if(arr_str_split[4].equalsIgnoreCase("WAIT"))
                    {
                        //Read Get response till ready or failure
                        readLine = responseReader.readLine();
                        str_To_Received.setLength(0);
                        str_To_Received.append(readLine);
                        arr_str_split = str_To_Received.toString().split(" ");
                        //Log whatever ready or failure reply
                        log.WriteToLogFile(str_To_Received.toString());
                    }

                    //Handle delete FAILURE (In case of failure Just log the message)
                    if(arr_str_split[4].equalsIgnoreCase("FAILURE"))
                    {
                        System.err.println("Deletion of File failed on server!!! File may not exist on server!!!");
                    }
                }
            }
            
        }
        catch (UnknownHostException e)
        {
            System.err.println("Unknown host!!!" + e.getMessage());
            System.exit(0);
        } catch (IOException e)
        {
            System.err.println("Couldn't get I/O for the connection to host");
            System.exit(0);
        } catch(Exception ex)
        {
            System.err.println("Exception caught!!");
            System.exit(0);
        }
        finally
        {
            //Garbage collection and do skill to kill client process

        }
    }

    private void SendBye()
    {
        StringBuffer str_To_Send = null;
        StringBuffer str_To_Received = null;
        BufferedReader responseReader = null;
        PrintWriter requestWriter = null;
        String readLine = null;
        try
        {
            str_To_Send = new StringBuffer();
            str_To_Received = new StringBuffer();
            //Initialize Streams
            responseReader = new BufferedReader(new InputStreamReader(clientSock.getInputStream()));
            requestWriter = new PrintWriter(clientSock.getOutputStream(),true);

            //Say bye request
            str_To_Send.append("Req Bye");
            requestWriter.println(str_To_Send.toString());
            requestWriter.flush();

            //Log Bye Request
            log.WriteToLogFile(str_To_Send.toString());

            //Read Bye response
            readLine = responseReader.readLine();

            str_To_Received.append(readLine);
            //Log Bye response
            log.WriteToLogFile(str_To_Received.toString());
        }
        catch (UnknownHostException e)
        {
            System.err.println("Unknown host!!!" + e.getMessage());
            System.exit(0);
        } catch (IOException e)
        {
            System.err.println("Couldn't get I/O for the connection to host");
            System.exit(0);
        } catch(Exception ex)
        {
            System.err.println("Exception caught!!");
            System.exit(0);
        }
        finally
        {
            //Garbage collection and do skill to kill client process

        }
    }

    private void DirectoryList(int startFileIndex, int noOfMaxFiles)
    {
        StringBuffer str_To_Send = null;
        StringBuffer str_To_Received = null;
        BufferedReader responseReader = null;
        PrintWriter requestWriter = null;
        String readLine = null;
        try
        {
            str_To_Send = new StringBuffer();
            str_To_Received = new StringBuffer();
            //Initialize Streams
            responseReader = new BufferedReader(new InputStreamReader(clientSock.getInputStream()));
            requestWriter = new PrintWriter(clientSock.getOutputStream(),true);

            //Send hello as handshaking
            if(SendHello()) { return; }    //If Send Hello fails , sequence ends

            //Send Directory List request
            str_To_Send.setLength(0);
            str_To_Send.append("Req list").append(" ").append(i_Req_No).append(" ");
            str_To_Send.append(startFileIndex).append(" ").append(noOfMaxFiles);

            //Send the data
            requestWriter.println(str_To_Send.toString());
            requestWriter.flush();

            //Log sent request
            log.WriteToLogFile(str_To_Send.toString());

            //Read response
            readLine = responseReader.readLine();
            str_To_Received.append(readLine);

            //Log Directory List response
            log.WriteToLogFile(str_To_Received.toString());
            
            String arr_str_split[] = str_To_Received.toString().split(" ");
            if(arr_str_split[0].equalsIgnoreCase("Rsp") && arr_str_split[1].equalsIgnoreCase("list"))
            {
                if(arr_str_split[2].equalsIgnoreCase(Integer.toString(i_Req_No)))
                {
                    if(arr_str_split[3].equalsIgnoreCase("SUCCESS"))
                    {
                        int actualNoOfFiles = 0;
                        actualNoOfFiles = Integer.parseInt(arr_str_split[5]);
                        System.out.println("Actual Number of Files Listed : " + actualNoOfFiles);
                        System.out.println("The file names are below: ");
                        for(int i = 1; i <= actualNoOfFiles; i++)
                        {
                            System.out.println(arr_str_split[5 + i]);   //Print each file name
                        }
                        System.out.println("Listing files complete.");
                    }
                    else if(arr_str_split[3].equalsIgnoreCase("FAILURE"))
                    {
                        System.err.println("Directory List failed on server!!! ");                        
                    }
                }
            }
            
        }
        catch (UnknownHostException e)
        {
            System.err.println("Unknown host!!!" + e.getMessage());
            System.exit(0);
        } catch (IOException e)
        {
            System.err.println("Couldn't get I/O for the connection to host");
            System.exit(0);
        } catch(Exception ex)
        {
            System.err.println("Exception caught!!");
            System.exit(0);
        }
        finally
        {
            //Garbage collection and do skill to kill client process

        }
    }


    public static void main(String args[])
    {        
        client cl = new client();
        try
        {            
            if(args.length <= 0)
            {
                System.err.println("Please enter required command line arguments and Run the program again!!");
                System.exit(0);
            }

            //Parse command line argument
            String readCommand = args[0];
            String fileName = null;
            boolean bFlag = false;
            int startFile = 0;
            int noOfMaxFile = 0;
            int noOfChunks = 0;
            int iPriority = 0;
            if(args.length > 0)
            {
                if(!readCommand.equalsIgnoreCase("hello") && !readCommand.equalsIgnoreCase("terminate") && !readCommand.equalsIgnoreCase("file put") && !readCommand.equalsIgnoreCase("file get") && !readCommand.equalsIgnoreCase("file aput") && !readCommand.equalsIgnoreCase("file aget") && !readCommand.equalsIgnoreCase("directory list") && !readCommand.equalsIgnoreCase("delete"))
                {
                    System.err.println("Invalid command entered!! Please rerun client program with valid commands!!");
                    System.exit(0);
                }
                else if(readCommand.equalsIgnoreCase("file put") || readCommand.equalsIgnoreCase("file get") || readCommand.equalsIgnoreCase("delete"))
                {
                    if(args.length < 3) {
                        System.err.println("Please rerun client program with all input parameters of command!!");
                        System.exit(0);
                    }
                    fileName = args[1];
                    iPriority = Integer.parseInt(args[2]);
                }
                else if(readCommand.equalsIgnoreCase("directory list"))
                {
                    if(args.length != 3)
                    {
                        System.err.println("Please rerun client program with required number of parameters to directory list command!!");
                        System.exit(0);
                    }
                    startFile = Integer.parseInt(args[1]);
                    noOfMaxFile = Integer.parseInt(args[2]);
                    if(noOfMaxFile < 1 || startFile < 0 )
                    {
                        System.err.println("Please rerun client program with correct values of arguments");
                        System.exit(0);
                    }
                }
                else if(readCommand.equalsIgnoreCase("file aput") || readCommand.equalsIgnoreCase("file aget"))
                {
                    if(args.length < 4)
                    {
                        System.err.println("Please rerun client program with required number of parameters to file aput or file aget command!!");
                        System.exit(0);
                    }
                    fileName = args[1];
                    noOfChunks = Integer.parseInt(args[2]);
                    iPriority = Integer.parseInt(args[3]);
                    if(noOfChunks <= 0)
                    {
                        System.err.println("Please rerun client program with required number of parameters to file aput or file aget command!!");
                        System.exit(0);
                    }
                }
                else if(readCommand.equalsIgnoreCase("delete"))
                {
                    if(args.length < 3)
                    {
                        System.err.println("Please rerun client program with required number of parameters to delete command!!");
                        System.exit(0);
                    }
                    fileName = args[1];
                    iPriority = Integer.parseInt(args[2]);
                }
            }

            //Connect to socket
            cl.clientSock = new Socket(cl.cfp.get_ServerName(),cl.cfp.get_PortNo());
            while(true)
            {
                if(bFlag == true)
                {
                    System.out.println("Next Operation: <hello | directory list | file put | file get | file aput | file aget | delete | terminate> :");
                    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                    String readLine = br.readLine();
                    String arr_str_split[] = readLine.split(" ");

                    if(arr_str_split.length >=1)
                    {
                        if(!arr_str_split[0].equalsIgnoreCase("hello") && !arr_str_split[0].equalsIgnoreCase("terminate") && !arr_str_split[0].equalsIgnoreCase("delete") && !arr_str_split[0].equalsIgnoreCase("directory") && !arr_str_split[0].equalsIgnoreCase("file"))
                        {
                            System.err.println("Invalid command entered!! Please enter valid commands!!");                            
                        }
                        else if (arr_str_split[0].equalsIgnoreCase("file"))
                        {
                            if(!arr_str_split[1].equalsIgnoreCase("get") && !arr_str_split[1].equalsIgnoreCase("aget") && !arr_str_split[1].equalsIgnoreCase("put") && !arr_str_split[1].equalsIgnoreCase("aput"))
                            {
                                System.err.println("Invalid command entered!! Please enter valid commands!!");
                            }
                        }
                        else if (arr_str_split[0].equalsIgnoreCase("directory"))
                        {
                            if(!arr_str_split[1].equalsIgnoreCase("list"))
                            {
                                System.err.println("Invalid command entered!! Please enter valid commands!!");
                            }
                        }
                    }
                    

                    cl.GetRandNumbers();    //Each time rand numbers changed
                    if(arr_str_split.length == 1)       //terminate and hello
                    {
                        readCommand = arr_str_split[0]; 
                    }else if(arr_str_split.length == 3) //Delete
                    {
                        readCommand = arr_str_split[0];
                        fileName = arr_str_split[1];
                        iPriority = Integer.parseInt(arr_str_split[2]);
                    }
                    else if(arr_str_split.length == 4) //file put and file get
                    {
                        readCommand = arr_str_split[0] + " " + arr_str_split[1];

                        if(arr_str_split[0].equalsIgnoreCase("file") && (arr_str_split[1].equalsIgnoreCase("put") || arr_str_split[1].equalsIgnoreCase("get")))
                        {
                            fileName = arr_str_split[2];
                            iPriority = Integer.parseInt(arr_str_split[3]);
                        }                   
                        else if(arr_str_split[0].equalsIgnoreCase("directory") && arr_str_split[1].equalsIgnoreCase("list")) //Directory List
                        {
                            startFile = Integer.parseInt(arr_str_split[2]);
                            noOfMaxFile = Integer.parseInt(arr_str_split[3]);
                        }                                                
                    }
                    else if(arr_str_split.length == 5) //file aput and aget with priority
                    {
                        readCommand = arr_str_split[0] + " " + arr_str_split[1];
                        //File aput and file aget
                        if(readCommand.equalsIgnoreCase("file aput") || readCommand.equalsIgnoreCase("file aget"))
                        {
                            fileName = arr_str_split[2];                        //file name
                            noOfChunks = Integer.parseInt(arr_str_split[3]);    //No of chunks after which abort
                            iPriority = Integer.parseInt(arr_str_split[4]); //Priority
                        }
                    }
                }
                if(bFlag == false)
                    bFlag = true;
                if(readCommand.equalsIgnoreCase("file put"))
                {
                    //File name to be uploaded is argument
                    System.out.println("Calling FilePut....");
                    cl.FilePut(fileName.toString(),iPriority);
                    System.out.println("FilePut Done.");
                }
                else if(readCommand.equalsIgnoreCase("file get"))
                {
                    //File name to be downloaded is argument
                    System.out.println("Calling FileGet....");
                    cl.FileGet(fileName.toString(),iPriority);
                    System.out.println("FileGet Done.");
                }
                if(readCommand.equalsIgnoreCase("file aput"))
                {
                    //File name to be uploaded is argument
                    System.out.println("Calling FilePut with abort....");
                    cl.FileaPut(fileName.toString(), noOfChunks,iPriority);
                    System.out.println("FilePut with abort Done.");
                }
                else if(readCommand.equalsIgnoreCase("file aget"))
                {
                    //File name to be downloaded is argument
                    System.out.println("Calling FileGet with abort....");
                    cl.FileaGet(fileName.toString(), noOfChunks,iPriority);
                    System.out.println("FileGet with abort Done.");
                }
                else if(readCommand.equalsIgnoreCase("directory list"))
                {
                    System.out.println("Calling Directory List...." + startFile + " " + noOfMaxFile );
                    cl.DirectoryList(startFile, noOfMaxFile);
                    System.out.println("Directory List Done.");
                }
                else if(readCommand.equalsIgnoreCase("hello"))
                {
                    System.out.println("Calling Hello....");
                    cl.SendHello();
                    System.out.println("Hello Done.");
                }
                else if(readCommand.equalsIgnoreCase("delete"))
                {
                    System.out.println("Calling Delete....");
                    cl.delete(fileName.toString(),iPriority);
                    System.out.println("Delete Done.");
                }
                else if(readCommand.equalsIgnoreCase("terminate"))
                {
                    System.out.println("Calling Terminate Connection....");
                    cl.SendBye();                    
                    cl.clientSock.close();
                    System.out.println("Client and server both said bye and client got terminated.");
                    break;
                }
                readCommand = "";
            }
        }
        catch(Exception ex)
        {
            System.err.println("Exception caught: "+ ex.getMessage());
            System.exit(0);
        }
    }

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

        public byte[] decodeFromString(String strOfBits, int chunkSize)
        {
            int i = 0;
            int j = 0;
            int k = 0;
            byte []arrByteRet = new byte[chunkSize];
            char zero = '0';
            char one = '1';
            boolean []bits = new boolean[8];
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
