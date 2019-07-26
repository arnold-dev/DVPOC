/* 
 * Copyright or © or Copr. Arnold Fertin 2019
 *
 * This software is a computer program whose purpose is to perform image processing.
 *
 * This software is governed by the CeCILL-C license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL-C license as circulated
 * by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL-C license and that you
 * accept its terms.
 */
package registration;

import correlation.Displacement;
import correlation.PhaseCorrelation3D;
import fht.FHT3D;
import ij.IJ;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import util.ConcurrencyUtils;
import volume.Dimensions;
import volume.Point3D;
import volume.VolumeFloatZz;
import volume.Window3D;

/**
 *
 * @author Arnold Fertin
 */
public final class RegistrationSinglePass
{
    private final RegistrationData data;

    private final DisplacementField field;

    private final Dimensions dims;

//    private Dimensions[] dimSerie;
    private AtomicInteger count;

    private int nOperations;

    public RegistrationSinglePass(final RegistrationData dat,
                                  final DisplacementField fld)
    {
        this.data = dat;
        this.field = fld;
        dims = new Dimensions(data.windowDimStart.dimX, data.windowDimStart.dimY, data.windowDimStart.dimZ);
    }

    public void match()
    {
        count = new AtomicInteger();
        showProgress();

        // Initialisation du champs de déplacement
        initializeField();

        // Préparation des fenêtrages et corrélateurs
//        final int n = getNumberOfIteration();
//        dimSerie = new Dimensions[n];
//        for (int i = 0, k = 1; i < n; i++, k *= 2)
//        {
//            final int dimX = Math.max(data.windowDimStart.dimX / k, data.windowDimEnd.dimX);
//            final int dimY = Math.max(c / k, data.windowDimEnd.dimY);
//            final int dimZ = Math.max(data.windowDimStart.dimZ / k, data.windowDimEnd.dimZ);
//            dimSerie[i] = new Dimensions(dimX, dimY, dimZ);
//        }
        // Calculs
        nOperations = field.getSize();
        final int nthreads = Math.min(ConcurrencyUtils.getNumberOfThreads(), nOperations);
        final Future<?>[] futures = new Future<?>[nthreads];
        final int k = nOperations / nthreads;
        for (int j = 0; j < nthreads; j++)
        {
            final int first = j * k;
            final int last;
            if (j == nthreads - 1)
            {
                last = field.getSize();
            }
            else
            {
                last = first + k;
            }
            futures[j] = ConcurrencyUtils.submit(new Processor(first, last));
        }
        ConcurrencyUtils.waitForCompletion(futures);
//        new Processor(0, field.getSize()).run();
    }

//    private int getNumberOfIteration()
//    {
//        int sizeX = data.windowDimStart.dimX;
//        int countX = 1;
//        while (sizeX > data.windowDimEnd.dimX)
//        {
//            sizeX /= 2;
//            countX++;
//        }
//        int sizeY = data.windowDimStart.dimY;
//        int countY = 1;
//        while (sizeY > data.windowDimEnd.dimY)
//        {
//            sizeY /= 2;
//            countY++;
//        }
//        int sizeZ = data.windowDimStart.dimZ;
//        int countZ = 1;
//        while (sizeZ > data.windowDimEnd.dimZ)
//        {
//            sizeZ /= 2;
//            countZ++;
//        }
//        return Math.max(Math.max(countX, countY), countZ);
//    }
    private void initializeField()
    {
        final Dimensions dim = data.volume1.getDimensions();
        final Window3D win = new Window3D(dim, data.windowType);
        final VolumeFloatZz v1 = win.apodize(data.volume1);
        final VolumeFloatZz v2 = win.apodize(data.volume2);
        FHT3D.zeroPaddingInZ(v1);
        FHT3D.zeroPaddingInZ(v2);
        final PhaseCorrelation3D co = new PhaseCorrelation3D(v1.getDimensions());
        final Displacement global = co.correlate(v1, v2, true, data.pValueLevel);

        field.initialize(global);
    }

    private synchronized void showProgress()
    {
        IJ.showProgress(count.incrementAndGet(), nOperations);
    }

    private class Processor implements Runnable
    {
        private final int from;

        private final int to;

        Processor(final int p1,
                  final int p2)
        {
            from = p1;
            to = p2;
        }

        @Override
        public void run()
        {
//            final int nIte = dimSerie.length;
//            boolean subPix = false;
//            for (int ite = 0; ite < nIte; ite++)
//            {
            final Window3D win = new Window3D(dims, data.windowType);
            final PhaseCorrelation3D corr = new PhaseCorrelation3D(dims);
            for (int j = from; j < to; j++)
            {
                final Point3D p1 = field.getPosition(j);
                final VolumeFloatZz v1 = win.apodizeSubVolume(p1, data.volume1);
                final Point3D p2 = field.getTranslatedPosition(j);
                final VolumeFloatZz v2 = win.apodizeSubVolume(p2, data.volume2);
                final Displacement tr = corr.correlate(v1, v2, true, data.pValueLevel);
                field.update(j, tr);
                showProgress();
            }
//            }
        }
    }
}
