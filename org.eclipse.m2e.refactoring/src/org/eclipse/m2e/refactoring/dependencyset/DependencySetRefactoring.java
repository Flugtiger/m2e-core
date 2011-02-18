/*******************************************************************************
 * Copyright (c) 2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.m2e.refactoring.dependencyset;

import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.*;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.ui.internal.editing.PomEdits.Operation;
import org.eclipse.m2e.refactoring.ChangeCreator;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * @author mkleint
 *
 */
public class DependencySetRefactoring extends Refactoring {

  private static final Logger LOG = LoggerFactory.getLogger(DependencySetRefactoring.class); 
  private final IFile file;
  private final List<ArtifactKey> keys;

  /**
   * @param file
   * @param groupId
   * @param artifactId
   * @param version
   */
  public DependencySetRefactoring(IFile file, List<ArtifactKey> keys) {
    this.file = file;
    this.keys = keys;
  }

  /* (non-Javadoc)
   * @see org.eclipse.ltk.core.refactoring.Refactoring#getName()
   */
  public String getName() {
    // TODO Auto-generated method stub
    return "Set dependency version";
  }

  /* (non-Javadoc)
   * @see org.eclipse.ltk.core.refactoring.Refactoring#checkInitialConditions(org.eclipse.core.runtime.IProgressMonitor)
   */
  public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    return new RefactoringStatus();
  }

  /* (non-Javadoc)
   * @see org.eclipse.ltk.core.refactoring.Refactoring#checkFinalConditions(org.eclipse.core.runtime.IProgressMonitor)
   */
  public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    return new RefactoringStatus();
  }

  /* (non-Javadoc)
   * @see org.eclipse.ltk.core.refactoring.Refactoring#createChange(org.eclipse.core.runtime.IProgressMonitor)
   */
  public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    CompositeChange res = new CompositeChange(getName());
    IStructuredModel model = null;
    try {
      model = StructuredModelManager.getModelManager().getModelForRead(file);
      IDocument document = model.getStructuredDocument();
      IStructuredModel tempModel = StructuredModelManager.getModelManager().createUnManagedStructuredModelFor(
          "org.eclipse.m2e.core.pomFile");
      tempModel.getStructuredDocument().setText(StructuredModelManager.getModelManager(), document.get());
      IDocument tempDocument = tempModel.getStructuredDocument();
      List<Operation> operations = new ArrayList<Operation>();
      for (ArtifactKey key : keys) {
        operations.add(new OneDependency(key));
      }
      CompoundOperation compound = new CompoundOperation(operations.toArray(new Operation[0]));
      performOnDOMDocument(new OperationTuple((IDOMModel) tempModel, compound));

      ChangeCreator chc = new ChangeCreator(file, document, tempDocument, getName());
      res.add(chc.createChange());
    } catch(Exception exc) {
      LOG.error("", exc);
    } finally {
      if(model != null) {
        model.releaseFromRead();
      }
    }
    return res;
  }
  
  private static class OneDependency implements Operation {

    private final String groupId;
    private final String artifactId;
    private final String version;
    
    public OneDependency(ArtifactKey key) {
      this.groupId = key.getGroupId();
      this.artifactId = key.getArtifactId();
      this.version = key.getVersion();
    }
    /* (non-Javadoc)
     * @see org.eclipse.m2e.core.ui.internal.editing.PomEdits.Operation#process(org.w3c.dom.Document)
     */
    public void process(Document document) {
      //TODO handle activated profiles?
      Element deps = findChild(document.getDocumentElement(), "dependencies");
      Element existing = findChild(deps, "dependency", childEquals("groupId", groupId),
          childEquals("artifactId", artifactId));
      if(existing != null) {
        //it's a direct dependency
        //TODO check the version value.. not to overwrite the existing version..
        //even better, have the action only available on transitive dependencies
        setText(getChild(existing, "version"), version);
      } else {
        //is transitive dependency
        Element dm = getChild(document.getDocumentElement(), "dependencyManagement", "dependencies");
        existing = findChild(dm, "dependency", childEquals("groupId", groupId),
            childEquals("artifactId", artifactId));
        if(existing != null) {
          setText(getChild(existing, "version"), version);
        } else {
          createDependency(dm, groupId, artifactId, version);
        }
      }
      
    }
    
  }

}
