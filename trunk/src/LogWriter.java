import java.io.*;

public class LogWriter
{
    private FileOutputStream fstream = null;
    private PrintStream print = null;

    LogWriter(String str_path)
    {
        try {

            fstream = new FileOutputStream(str_path,true);
            print = new PrintStream(fstream);
            
        } catch (FileNotFoundException ex) {
            System.err.println("Exception :" + ex.getMessage());
            System.exit(0);
        }
    }

    public void WriteToLogFile(String str_toWrite)
    {
        print.println(str_toWrite);
        print.flush();
    }

    public void ClosePrintStream()
    {
        print.close();
    }
}
