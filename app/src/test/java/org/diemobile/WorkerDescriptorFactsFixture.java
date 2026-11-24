// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.system.StructStat;

import org.robolectric.util.ReflectionHelpers;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Explicit host-test observations; stock Robolectric fstat/fcntl are not kernel FD observations.
 */
final class WorkerDescriptorFactsFixture implements BinaryWorkerService.DescriptorInspector {
    private final Map<ParcelFileDescriptor, BinaryWorkerService.DescriptorFacts> entries =
            new IdentityHashMap<>();
    private final Map<ParcelFileDescriptor, Integer> failAt = new IdentityHashMap<>();
    private final Map<ParcelFileDescriptor, Integer> visits = new IdentityHashMap<>();

    static WorkerDescriptorFactsFixture safe(Bundle request) {
        WorkerDescriptorFactsFixture fixture = new WorkerDescriptorFactsFixture();
        long inode = 100;
        for (String name : new String[] {"input", "output", "payload", "raster"}) {
            ParcelFileDescriptor descriptor = request.getParcelable(name);
            if (descriptor != null && !fixture.entries.containsKey(descriptor)) {
                fixture.put(
                        descriptor,
                        regular(
                                1,
                                inode++,
                                Math.max(0, descriptor.getStatSize()),
                                name.equals("input")
                                        ? OsConstants.O_RDONLY
                                        : OsConstants.O_WRONLY));
            }
        }
        return fixture;
    }

    WorkerDescriptorFactsFixture put(
            ParcelFileDescriptor descriptor, BinaryWorkerService.DescriptorFacts facts) {
        entries.put(descriptor, facts);
        return this;
    }

    WorkerDescriptorFactsFixture failOnInspection(ParcelFileDescriptor descriptor, int visit) {
        failAt.put(descriptor, visit);
        return this;
    }

    void attach(BinaryWorkerService service) {
        ReflectionHelpers.setField(service, "descriptorInspector", this);
    }

    @Override
    public BinaryWorkerService.DescriptorFacts inspect(ParcelFileDescriptor descriptor)
            throws IOException {
        int visit = visits.getOrDefault(descriptor, 0) + 1;
        visits.put(descriptor, visit);
        if (visit == failAt.getOrDefault(descriptor, -1))
            throw new IOException("Explicit test descriptor inspection failure");
        if (!entries.containsKey(descriptor))
            throw new IOException("No explicit test descriptor facts were supplied");
        return entries.get(descriptor);
    }

    static BinaryWorkerService.DescriptorFacts regular(
            long device, long inode, long size, int flags) {
        return facts(device, inode, OsConstants.S_IFREG | 0600, size, flags);
    }

    static BinaryWorkerService.DescriptorFacts facts(
            long device, long inode, int mode, long size, int flags) {
        return new BinaryWorkerService.DescriptorFacts(
                new StructStat(device, inode, mode, 1, 1, 1, 0, size, 0, 0, 0, 4096, 1), flags);
    }
}
