import com.xinhaoyuan.andchecker.ACInstrumentation;
import com.xinhaoyuan.andchecker.ACICommand;

class TestCommand extends ACICommand {
    public String getName() { return "Test"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mResult.append(" OK Helloworld");
    }
}
