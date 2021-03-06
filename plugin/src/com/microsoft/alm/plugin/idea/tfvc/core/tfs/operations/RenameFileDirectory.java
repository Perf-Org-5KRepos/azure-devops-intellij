// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.microsoft.alm.plugin.idea.tfvc.core.tfs.operations;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.microsoft.alm.helpers.Path;
import com.microsoft.alm.plugin.external.models.PendingChange;
import com.microsoft.alm.plugin.external.models.ServerStatusType;
import com.microsoft.alm.plugin.external.utils.CommandUtils;
import com.microsoft.alm.plugin.idea.tfvc.core.TFSVcs;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rename a file or a directory using the tf command line
 */
public class RenameFileDirectory {
    public static final Logger logger = LoggerFactory.getLogger(RenameFileDirectory.class);

    public static void execute(final PsiElement element, final String newName, final UsageInfo[] usages,
                               @Nullable final RefactoringElementListener listener) throws IncorrectOperationException {
        try {
            final VirtualFile virtualFile;
            if (element instanceof PsiFile) {
                logger.info("Renaming file...");
                virtualFile = ((PsiFile) element).getVirtualFile();
            } else if (element instanceof PsiDirectory) {
                logger.info("Renaming directory...");
                virtualFile = ((PsiDirectory) element).getVirtualFile();
            } else {
                // should never reach here since we check if file/directory before making a rename
                logger.warn("RenameFile: failed to find proper object to rename: " + element.getClass());
                throw new IncorrectOperationException("Can't perform rename on objects other than files and directories");
            }

            final String currentPath = virtualFile.getPath();
            final String parentDirectory = virtualFile.getParent().getPath();
            final String newPath = Path.combine(parentDirectory, newName);
            final Project project = element.getProject();

            // a single file may have 0, 1, or 2 pending changes to it
            // 0 - file has not been touched in the local workspace
            // 1 - file has versioned OR unversioned changes
            // 2 - file has versioned AND unversioned changes (rare but can happen)
            final List<PendingChange> pendingChanges = new ArrayList<>(2);
            pendingChanges.addAll(
                    CommandUtils.getStatusForFiles(
                            project,
                            TFSVcs.getInstance(project).getServerContext(true),
                            ImmutableList.of(currentPath)));

            // ** Rename logic **
            // If 1 change and it's an add that means it's a new unversioned file so rename thru the file system
            // Anything else can be renamed
            // Deleted files should not be at this point since IDE disables rename option for them
            if (pendingChanges.size() == 1 && pendingChanges.get(0).getChangeTypes().contains(ServerStatusType.ADD)) {
                logger.info("Renaming unversioned file thru file system");
                RenameUtil.doRenameGenericNamedElement(element, newName, usages, listener);
            } else {
                logger.info("Renaming file thru tf commandline");
                CommandUtils.renameFile(TFSVcs.getInstance(project).getServerContext(true), currentPath, newPath);

                // this alerts that a rename has taken place so any additional processing can take place
                final VFileEvent event = new VFilePropertyChangeEvent(element.getManager(), virtualFile, "name", currentPath, newName, false);
                PersistentFS.getInstance().processEvents(Collections.singletonList(event));
            }
        } catch (Throwable t) {
            logger.warn("renameElement experienced a failure while trying to rename a file", t);
            throw new IncorrectOperationException(t);
        }

        if (listener != null) {
            listener.elementRenamed(element);
        }
    }
}