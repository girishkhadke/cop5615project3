
import java.io.*;
/**
 *
 * Created By: Girish Khadke
 * Date: 11 Sept 2010
 */
public class ConfigFileParser
{
    private int port_no;
    private int chunk_size;
    private String server_name;
    private String client_name;
    
    ConfigFileParser(String str_file_path)
    {
        FileInputStream fstream = null;
        DataInputStream dstream = null;
        BufferedReader br = null;
        InputStreamReader istream = null;
        String str_line;
        String str_delim = ":";
        String []arr_str_split;
        
        try
        {
            fstream = new FileInputStream(str_file_path);
            dstream = new DataInputStream(fstream);
            istream = new InputStreamReader(dstream);
            br = new BufferedReader(istream);

            //Parse Server name
            str_line = br.readLine();
            arr_str_split = str_line.split(str_delim);            
            this.set_ServerName(arr_str_split[1]);

            //Parse Port Number
            str_line = br.readLine();
            arr_str_split = str_line.split(str_delim);
            this.set_PortNo(Integer.parseInt(arr_str_split[1]));

            //Parse Chunk Size
            str_line = br.readLine();
            arr_str_split = str_line.split(str_delim);
            this.set_ChunkSize(Integer.parseInt(arr_str_split[1]));

            //Parse Client name
            str_line = br.readLine();
            arr_str_split = str_line.split(str_delim);
            this.set_ClientName(arr_str_split[1]);
                       
        }
        catch(Exception e)
        {
            System.err.println("ConfigFileParser.java: Parsing Config.ini file failed!!" + e.getMessage());
            System.exit(0);
        }
    }
   
    public int get_PortNo()
    {
        return this.port_no;
    }

    public int get_ChunkSize()
    {
        return this.chunk_size;
    }

    public String get_ServerName()
    {
        return this.server_name;
    }

    public void set_PortNo(int int_port_no)
    {
        this.port_no = int_port_no;

    }

    public void set_ChunkSize(int int_chunk_size)
    {
        this.chunk_size = int_chunk_size;
    }

    public void set_ServerName(String str_server_name)
    {
        this.server_name = str_server_name;
    }

     public String get_ClientName()
    {
        return this.client_name;
    }

    public void set_ClientName(String str_client_name)
    {
        this.client_name = str_client_name;
    }
    
}
