// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.external.tools;

import com.microsoft.alm.plugin.external.exceptions.ToolBadExitCodeException;
import com.microsoft.alm.plugin.external.exceptions.ToolException;
import com.sun.jna.Platform;
import org.apache.commons.lang.StringUtils;

import java.io.File;

/**
 * This is a static class that provides helper methods for running the TF command line.
 */
public class TfTool {
    private static final String[] TF_WINDOWS_PROGRAMS = {"tf.exe", "tf.bat", "tf.cmd"};
    private static final String[] TF_OTHER_PROGRAMS = {"tf", "tf.sh"};

    /**
     * This method returns the path to the TF command line program.
     * It relies on the TF_HOME env var to be set.
     * This method will throw if no valid path exists.
     */
    public static String getValidLocation() {
        // Get the home location for TF
        final String location = getLocation();
        if (location == null) {
            // TF_HOME not set
            throw new ToolException(ToolException.KEY_TF_HOME_NOT_SET);
        } else if (StringUtils.isEmpty(location)) {
            // No program was found in TF_HOME folder
            throw new ToolException(ToolException.KEY_TF_EXE_NOT_FOUND);
        } else {
            return location;
        }
    }

    /**
     * This method returns the path to the TF command line program.
     * it doesn't throw
     */
    public static String getLocation() {
        // Get the home location for TF
        final String home = System.getenv("TF_HOME");
        if (home == null) {
            return null;
        }

        final String[] filenames = Platform.isWindows() ? TF_WINDOWS_PROGRAMS : TF_OTHER_PROGRAMS;

        // Try each program name in order. Return the first one that exists.
        for (final String filename : filenames) {
            final File tfFile = new File(home, filename);
            if (tfFile.exists()) {
                return tfFile.toString();
            }
        }

        // No program was found in that folder
        return StringUtils.EMPTY;
    }

    /**
     * Use this method to check the TF exit code and throw an appropriate exception.
     */
    public static void throwBadExitCode(final int exitCode) {
        if (exitCode != 0) {
            // TODO distinguish between warnings and fatal errors
            throw new ToolBadExitCodeException(exitCode);
        }
    }
}