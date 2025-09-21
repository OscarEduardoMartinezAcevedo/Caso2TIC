import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/**
 * Opcion1 — Generación de referencias (trazas) para C = A + B.
 * Cumple el formato del anexo:
 *   Cabecera:
 *     TP=<int>
 *     NF=<int>
 *     NC=<int>
 *     NR=<int>   (== 3 * NF * NC)
 *     NP=<int>   (== ceil(3 * NF * NC * 4 / TP))
 *   Cuerpo (por celda i,j en row-major):
 *     M1:[i-j],<page>,<offset>,r
 *     M2:[i-j],<page>,<offset>,r
 *     M3:[i-j],<page>,<offset>,w
 */
public class Opcion1 {
    private String lastError;

    public String getLastError() { return lastError; }
    private boolean fail(String msg) { this.lastError = msg; return false; }

    /**
     * Genera proc<i>.txt para cada proceso.
     *
     * @param tp       tamaño de página (bytes) > 0
     * @param nproc    número de procesos > 0
     * @param sizes    lista de tamaños por proceso; cada item es int[]{NF,NC}, ambos > 0
     * @param elemSize tamaño del elemento EN BYTES (debe ser 4 en este caso)
     * @param outDir   carpeta de salida existente y escribible
     * @return true si todos los archivos se escribieron correctamente; en otro caso false + getLastError()
     */
    public boolean runOpcion1(int tp, int nproc, List<int[]> sizes, int elemSize, Path outDir) {
        // Por contrato con la UI, asumimos datos válidos; mantenemos defensas mínimas.
        if (tp <= 0 || nproc <= 0 || elemSize != 4 || sizes == null || sizes.size() != nproc || outDir == null) {
            return fail("Parametros invalidos para Opcion1 (verifique con la UI).");
        }

        for (int pid = 0; pid < nproc; pid++) {
            int[] sz = sizes.get(pid);
            if (sz == null || sz.length != 2) return fail("Tamano invalido en proceso " + pid);
            int nf = sz[0], nc = sz[1];
            if (nf <= 0 || nc <= 0) return fail("NF y NC deben ser > 0 (proc " + pid + ").");

            // Cálculos principales
            long cells = (long) nf * (long) nc;           // elementos por matriz
            long nr    = 3L * cells;                      // R(M1), R(M2), W(M3) por celda
            long bytes = nr / 3L * elemSize * 3L;         // == 3 * nf * nc * 4
            long np    = ceilDiv(bytes, tp);              // páginas virtuales necesarias

            // Bases en DV (row-major, contiguas): m1=0; m2=|m1|; m3=|m1|+|m2|
            long bytesPerMatrix = cells * elemSize;
            long baseM1 = 0L;
            long baseM2 = bytesPerMatrix;
            long baseM3 = bytesPerMatrix * 2L;

            // Construir contenido del archivo
            StringBuilder sb = new StringBuilder(Math.max(1024, (int)Math.min(nr, 1000)*32));
            appendHeader(sb, tp, nf, nc, nr, np);

            // Secuencia por celda (i,j): R(M1) → R(M2) → W(M3) — row-major
            for (int i = 0; i < nf; i++) {
                for (int j = 0; j < nc; j++) {
                    long lin = (long) i * (long) nc + (long) j;

                    long dv1 = baseM1 + lin * elemSize;
                    long dv2 = baseM2 + lin * elemSize;
                    long dv3 = baseM3 + lin * elemSize;

                    long page1 = dv1 / tp, off1 = dv1 % tp;
                    long page2 = dv2 / tp, off2 = dv2 % tp;
                    long page3 = dv3 / tp, off3 = dv3 % tp;

                    appendAccess(sb, 1, i, j, page1, off1, 'r');
                    appendAccess(sb, 2, i, j, page2, off2, 'r');
                    appendAccess(sb, 3, i, j, page3, off3, 'w');
                }
            }

            // Escribir a proc<i>.txt
            Path outFile = outDir.resolve("proc" + pid + ".txt");
            try {
                Files.writeString(outFile, sb.toString(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("[Opcion1] Escrito " + outFile.getFileName()
                        + "  (TP=" + tp + ", NF=" + nf + ", NC=" + nc + ", NR=" + nr + ", NP=" + np + ")");
            } catch (IOException e) {
                return fail("No se pudo escribir " + outFile + ": " + e.getMessage());
            }
        }
        return true;
    }

    private static long ceilDiv(long a, long b) {
        if (b <= 0) throw new IllegalArgumentException("Divisor no positivo en ceilDiv");
        return (a + b - 1) / b;
    }

    private static void appendHeader(StringBuilder sb, int tp, int nf, int nc, long nr, long np) {
        sb.append("TP=").append(tp).append('\n');
        sb.append("NF=").append(nf).append('\n');
        sb.append("NC=").append(nc).append('\n');
        sb.append("NR=").append(nr).append('\n');
        sb.append("NP=").append(np).append('\n');
    }

    private static void appendAccess(StringBuilder sb, int mId, int i, int j, long page, long offset, char mode) {
        // Formato exacto del anexo: Mx:[i-j],<page>,<offset>,<r|w>
        sb.append('M').append(mId).append(":[")
          .append(i).append('-').append(j).append("],")
          .append(page).append(',').append(offset).append(',')
          .append(mode).append('\n');
    }
}
