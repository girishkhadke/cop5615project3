
public class OpenFiles
{
    public String str_OpenFileName;
    public String str_Operation;
    public int i_Priority;

    OpenFiles()
    {
        this.str_OpenFileName = "";
        this.str_Operation = "";
        this.i_Priority = 0;
    }

    OpenFiles(String str_FileName, String str_Oper, int iPriority)
    {
        this.str_OpenFileName = str_FileName;
        this.str_Operation = str_Oper;
        this.i_Priority = iPriority;
    }
}
