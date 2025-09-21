
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class UI {
// Interfaz.java
// Coloca este archivo en el MISMO paquete/directorio donde están Opcion1 y Opcion2.
// Si usas package, añade: package simulador;


    public static void main(String[] args) {
        new UI().ejecutarUnaVez();
    }

    // Ejecución única: pide datos, valida, invoca UNA de las dos opciones y termina.
    public void ejecutarUnaVez() {
        try (Scanner in = new Scanner(System.in)) {
            System.out.println("=== Simulador Caso 2 — Interfaz ===");
            int opcion = pedirOpcion(in);

            try {
                switch (opcion) {
                    case 1 -> ejecutarOpcion1(in);
                    case 2 -> ejecutarOpcion2(in);
                    case 0 -> System.out.println("Saliendo. ¡Gracias!");
                    default -> System.out.println("Opción no válida.");
                }
            } catch (IllegalArgumentException iae) {
                System.out.println("[ERROR] " + iae.getMessage());
            } catch (Exception ex) {
                System.out.println("[ERROR INESPERADO] " + ex.getMessage());
            }
        }
    }

    // -------------------- Opción 1 --------------------
    private void ejecutarOpcion1(Scanner in) {
        System.out.println(">>> Opción 1 — Generación de referencias (trazas)");

        int tp = pedirEnteroPositivo(in, "Tamaño de página TP (bytes): ");
        int nproc = pedirEnteroPositivo(in, "Número de procesos NPROC: ");

        System.out.print("Tamaños por proceso (NFxNC separados por coma, ej.: 4x4,8x8): ");
        String tamsRaw = in.nextLine().trim();
        List<int[]> sizes = parsearValidarTamanos(tamsRaw, nproc);

        int elemSize = pedirEnteroPositivoPorDefecto(in, "Tamaño de elemento (bytes) [Enter = 4]: ", 4);

        Path outDir = pedirDirectorio(in, "Carpeta de salida para proc<i>.txt: ");
        confirmarSobrescrituraSiHayArchivos(in, outDir); // y/n

        // Instanciar SOLO la clase necesaria
        Opcion1 opcion1 = new Opcion1();

        boolean ok = opcion1.runOpcion1(tp, nproc, sizes, elemSize, outDir);
        if (!ok) {
            String why = opcional(opcion1.getLastError(), "Fallo en Opción 1.");
            throw new IllegalArgumentException(why);
        }

        // Nota: Opcion1 imprime por consola su progreso y resultado.
        System.out.println(">>> Opción 1 finalizada.");
    }

    // -------------------- Opción 2 --------------------
    private void ejecutarOpcion2(Scanner in) {
        System.out.println(">>> Opción 2 — Simulación de ejecución");

        Path inDir = pedirDirectorio(in, "Carpeta que contiene proc<i>.txt: ");
        List<Path> procFiles = listarProcFiles(inDir);
        if (procFiles.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron archivos proc<i>.txt en " + inDir);
        }

        int nproc = pedirEnteroPositivoPorDefecto(in,
                "Número de procesos [Enter = detectar por archivos (" + procFiles.size() + ")]: ",
                procFiles.size());

        validarProcFilesExactos(inDir, nproc);

        int totalFrames = pedirEnteroPositivo(in, "Número total de marcos (sugerido múltiplo de " + nproc + "): ");

        // Instanciar SOLO la clase necesaria
        Opcion2 opcion2 = new Opcion2();

        boolean ok = opcion2.runOpcion2(nproc, totalFrames, inDir);
        if (!ok) {
            String why = opcional(opcion2.getLastError(), "Fallo en Opción 2.");
            throw new IllegalArgumentException(why);
        }

        // Nota: Opcion2 imprime por consola todo (event log y resultados).
        System.out.println(">>> Opción 2 finalizada.");
    }

    // -------------------- Helpers de validación / I/O --------------------

    private int pedirOpcion(Scanner in) {
        System.out.println("""
            ----------------------------
            Seleccione una opción:
              [1] Opción 1 - Generar referencias (trazas)
              [2] Opción 2 - Simular ejecución
              [0] Salir
            ----------------------------""");
        System.out.print("Opción: ");
        String line = in.nextLine().trim();
        if (!line.matches("\\d+")) throw new IllegalArgumentException("Debe ingresar un número (0,1,2).");
        int val = Integer.parseInt(line);
        if (val < 0 || val > 2) throw new IllegalArgumentException("Opción fuera de rango.");
        return val;
    }

    private int pedirEnteroPositivo(Scanner in, String prompt) {
        System.out.print(prompt);
        String s = in.nextLine().trim();
        if (!s.matches("\\d+")) throw new IllegalArgumentException("Debe ingresar un entero positivo.");
        int v = Integer.parseInt(s);
        if (v <= 0) throw new IllegalArgumentException("El valor debe ser > 0.");
        return v;
    }

    private int pedirEnteroPositivoPorDefecto(Scanner in, String prompt, int def) {
        System.out.print(prompt);
        String s = in.nextLine().trim();
        if (s.isEmpty()) return def;
        if (!s.matches("\\d+")) throw new IllegalArgumentException("Debe ingresar un entero positivo.");
        int v = Integer.parseInt(s);
        if (v <= 0) throw new IllegalArgumentException("El valor debe ser > 0.");
        return v;
    }

    private Path pedirDirectorio(Scanner in, String prompt) {
        System.out.print(prompt);
        String s = in.nextLine().trim();
        if (s.isEmpty()) throw new IllegalArgumentException("La ruta no puede ser vacía.");
        Path p = Paths.get(s);
        if (!Files.exists(p)) throw new IllegalArgumentException("La ruta no existe: " + p);
        if (!Files.isDirectory(p)) throw new IllegalArgumentException("No es un directorio: " + p);
        return p;
    }

    // Confirmación estricta y/n
    private boolean pedirSiNo(Scanner in, String prompt) {
        System.out.print(prompt);
        String s = in.nextLine().trim();
        if (s.equals("y")) return true;
        if (s.equals("n")) return false;
        throw new IllegalArgumentException("Responda 'y' o 'n'.");
    }

    private List<int[]> parsearValidarTamanos(String csv, int esperados) {
        if (csv.isEmpty()) throw new IllegalArgumentException("La lista de tamaños no puede estar vacía.");
        String[] parts = csv.split("\\s*,\\s*");
        if (parts.length != esperados) {
            throw new IllegalArgumentException("Se esperaban " + esperados + " tamaños (NFxNC) separados por coma.");
        }
        Pattern pat = Pattern.compile("(?i)^(\\d+)x(\\d+)$");
        List<int[]> res = new ArrayList<>(parts.length);
        for (String px : parts) {
            var m = pat.matcher(px.trim());
            if (!m.matches()) {
                throw new IllegalArgumentException("Formato inválido '" + px + "'. Use NFxNC, p. ej., 8x8.");
            }
            int nf = Integer.parseInt(m.group(1));
            int nc = Integer.parseInt(m.group(2));
            if (nf <= 0 || nc <= 0) {
                throw new IllegalArgumentException("NF y NC deben ser > 0 (dato inválido en '" + px + "').");
            }
            res.add(new int[]{nf, nc});
        }
        return res;
    }

    private void confirmarSobrescrituraSiHayArchivos(Scanner in, Path outDir) {
        boolean any = !listarProcFiles(outDir).isEmpty();
        if (any) {
            boolean overwrite = pedirSiNo(in, "Se encontraron proc<i>.txt en esa carpeta. ¿Sobrescribir? (y/n): ");
            if (!overwrite) throw new IllegalArgumentException("Operación cancelada por el usuario.");
        }
    }

    private List<Path> listarProcFiles(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().matches("proc\\d+\\.txt"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private void validarProcFilesExactos(Path dir, int nproc) {
        // deben existir proc0.txt ... proc{nproc-1}.txt sin faltar ni sobrar
        for (int pid = 0; pid < nproc; pid++) {
            Path f = dir.resolve("proc" + pid + ".txt");
            if (!Files.exists(f)) {
                throw new IllegalArgumentException("Falta el archivo " + f.getFileName() + " en " + dir);
            }
        }
        List<Path> all = listarProcFiles(dir);
        if (all.size() != nproc) {
            throw new IllegalArgumentException("Se encontraron " + all.size() + " archivos proc<i>.txt, pero NPROC=" + nproc + ".");
        }
    }

    private static String opcional(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}
