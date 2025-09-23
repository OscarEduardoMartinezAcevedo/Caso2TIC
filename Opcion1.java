import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class Opcion1 {
    private String lastError;

    public String getLastError() { return lastError; }
    private boolean fail(String msg) { this.lastError = msg; return false; }

//Genera proc<i>.txt para cada proceso, verifica si los datos son correctos    
public boolean runOpcion1(int tp, int nproc, List<int[]> sizes, int elemSize, Path outDir) {
    for (int pid = 0; pid < nproc; pid++) {
        int[] sz = sizes.get(pid);
        int nf = sz[0], nc = sz[1];

        long cells = (long) nf * (long) nc;
        long nr    = 3L * cells;                 // R,R,W por celda
        long bytes = 3L * cells * elemSize;      // tamaño total contiguo m1|m2|m3
        long np    = (bytes + tp - 1L) / tp;     // ceil(bytes/TP)

        long rowBytes = (long) nc * elemSize;    // bytes por fila
        long bytesM1  = cells * elemSize;        // tamaño de una matriz
        // Bases en (page,offset):
        long baseP1 = 0,            baseO1 = 0;
        long baseP2 = bytesM1 / tp, baseO2 = bytesM1 % tp;
        long baseP3 = (2L*bytesM1) / tp, baseO3 = (2L*bytesM1) % tp;

        StringBuilder sb = new StringBuilder(Math.max(1024, (int)Math.min(nr, 1000)*32));
        sb.append("TP=").append(tp).append('\n');
        sb.append("NF=").append(nf).append('\n');
        sb.append("NC=").append(nc).append('\n');
        sb.append("NR=").append(nr).append('\n');
        sb.append("NP=").append(np).append('\n');

        for (int i = 0; i < nf; i++) {
            long baseRowDelta = (long) i * rowBytes; // bytes desde el inicio de la matriz hasta la fila i
            for (int j = 0; j < nc; j++) {
                long delta = baseRowDelta + (long) j * elemSize;

                long page1 = baseP1 + (baseO1 + delta) / tp;
                long off1  = (baseO1 + delta) % tp;

                long page2 = baseP2 + (baseO2 + delta) / tp;
                long off2  = (baseO2 + delta) % tp;

                long page3 = baseP3 + (baseO3 + delta) / tp;
                long off3  = (baseO3 + delta) % tp;

                sb.append("M1:[").append(i).append('-').append(j).append("],")
                  .append(page1).append(',').append(off1).append(",r\n");

                sb.append("M2:[").append(i).append('-').append(j).append("],")
                  .append(page2).append(',').append(off2).append(",r\n");

                sb.append("M3:[").append(i).append('-').append(j).append("],")
                  .append(page3).append(',').append(off3).append(",w\n");
            }
        }
// Escribe procs
        Path outFile = outDir.resolve("proc" + pid + ".txt");
        try {
            Files.writeString(outFile, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("[Opcion1] Escrito " + outFile.getFileName()
                + " (TP=" + tp + ", NF=" + nf + ", NC=" + nc + ", NR=" + nr + ", NP=" + np + ")");
        } catch (IOException e) {
            this.lastError = "No se pudo escribir " + outFile + ": " + e.getMessage();
            return false;
        }
    }
    return true;
}
}