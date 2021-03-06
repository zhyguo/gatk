/*
* Copyright (c) 2012 The Broad Institute
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package org.broadinstitute.sting.utils.runtime;

import com.sun.corba.se.spi.orbutil.fsm.Input;

import java.io.File;
import java.util.Map;

public class ProcessSettings {
    private String[] command;
    private Map<String, String> environment;
    private File directory;
    private boolean redirectErrorStream;
    private InputStreamSettings stdinSettings;
    private OutputStreamSettings stdoutSettings;
    private OutputStreamSettings stderrSettings;

    /**
     * @param command Command line to run.
     */
    public ProcessSettings(String[] command) {
        this(command, false, null, null, null, null, null);
    }

    /**
     * @param command             Command line to run.
     * @param redirectErrorStream true if stderr should be sent to stdout.
     * @param environment         Environment settings to override System.getEnv, or null to use System.getEnv.
     * @param directory           The directory to run the command in, or null to run in the current directory.
     * @param stdinSettings       Settings for writing to the process stdin.
     * @param stdoutSettings      Settings for capturing the process stdout.
     * @param stderrSettings      Setting for capturing the process stderr.
     */
    public ProcessSettings(String[] command, boolean redirectErrorStream, File directory, Map<String, String> environment,
                           InputStreamSettings stdinSettings, OutputStreamSettings stdoutSettings, OutputStreamSettings stderrSettings) {
        this.command = checkCommand(command);
        this.redirectErrorStream = redirectErrorStream;
        this.directory = directory;
        this.environment = environment;
        this.stdinSettings = checkSettings(stdinSettings);
        this.stdoutSettings = checkSettings(stdoutSettings);
        this.stderrSettings = checkSettings(stderrSettings);
    }

    public String[] getCommand() {
        return command;
    }

    public void setCommand(String[] command) {
        this.command = checkCommand(command);
    }

    public boolean isRedirectErrorStream() {
        return redirectErrorStream;
    }

    public void setRedirectErrorStream(boolean redirectErrorStream) {
        this.redirectErrorStream = redirectErrorStream;
    }

    public File getDirectory() {
        return directory;
    }

    public void setDirectory(File directory) {
        this.directory = directory;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment;
    }

    public InputStreamSettings getStdinSettings() {
        return stdinSettings;
    }

    public void setStdinSettings(InputStreamSettings stdinSettings) {
        this.stdinSettings = checkSettings(stdinSettings);
    }

    public OutputStreamSettings getStdoutSettings() {
        return stdoutSettings;
    }

    public void setStdoutSettings(OutputStreamSettings stdoutSettings) {
        this.stdoutSettings = checkSettings(stdoutSettings);
    }

    public OutputStreamSettings getStderrSettings() {
        return stderrSettings;
    }

    public void setStderrSettings(OutputStreamSettings stderrSettings) {
        this.stderrSettings = checkSettings(stderrSettings);
    }

    protected String[] checkCommand(String[] command) {
        if (command == null)
            throw new IllegalArgumentException("Command is not allowed to be null");
        for (String s: command)
            if (s == null)
                throw new IllegalArgumentException("Command is not allowed to contain nulls");
        return command;
    }

    protected InputStreamSettings checkSettings(InputStreamSettings settings) {
        return settings == null ? new InputStreamSettings() : settings;
    }

    protected OutputStreamSettings checkSettings(OutputStreamSettings settings) {
        return settings == null ? new OutputStreamSettings() : settings;
    }
}
