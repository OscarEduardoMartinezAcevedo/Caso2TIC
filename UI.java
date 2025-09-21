import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class UI {

    public static void main(String[] args) {
        new UI().ejecutarUnaVez();
    }

    // Ejecución única: pide datos, valida, invoca UNA de las dos opciones y termina.
    public void ejecutarUnaVez() {
        try (Scanner in = new Scanner(System.in)) {
            System.out.println("=== Simulador Caso 2 — Interfaz ===");
            int opcion = Opciones(in);

            try {
                switch (opcion) {
                    case 1 -> ejecutarOpcion1(in);
                    case 2 -> ejecutarOpcion2(in);
                    case 0 -> System.out.println("Saliendo...");
                    default -> System.out.println("Opción no valida.");
                }
            } catch (IllegalArgumentException iae) {
                System.out.println("[ERROR] " + iae.getMessage());
            } catch (Exception ex) {
                System.out.println("[ERROR INESPERADO] " + ex.getMessage());
            }
        }
    }

    // ==========================
    // Opción 1 — Generación de referencias (trazas)
    // Debe LEER un archivo de configuración con TP, NPROC, TAMS (NFxNC,...)
    // ==========================
    private void ejecutarOpcion1(Scanner in) {
        System.out.println("Opcion 1 — Generacion de referencias (trazas)");
        Path configPath = archivo(in, "Ruta del archivo de configuración (p. ej., config.txt): ");
        Config cfg = leerConfig(configPath); // valida TP>0, NPROC>0, TAMS formato NFxNC y cantidad == NPROC

        // Tamaño de elemento SIEMPRE 4 bytes (enteros de 32 bits)
        final int elemSize = 4;

        Path outDir = directorio(in, "Carpeta de salida para proc<i>.txt: ");
        sobrescrituraArchivos(in, outDir); // confirma y/n

        // Instanciar SOLO la clase necesaria
        Opcion1 opcion1 = new Opcion1();

        boolean ok = opcion1.runOpcion1(cfg.tp, cfg.nproc, cfg.sizes, elemSize, outDir);
        if (!ok) {
            String why = opcional(opcion1.getLastError(), "Fallo en Opcion 1.");
            throw new IllegalArgumentException(why);
        }

        System.out.println("Opcion 1 finalizada.");
    }

    // ==========================
    // Opción 2 — Simulación de ejecución
    // Debe correr con #marcos múltiplo de #procesos y cargar dv de proc<i>.txt
    // ==========================
    private void ejecutarOpcion2(Scanner in) {
        System.out.println(">>> Opcion 2 — Simulacion de ejecucion");

        Path inDir = directorio(in, "Carpeta que contiene proc<i>.txt: ");
        List<Path> procFiles = listaProcFiles(inDir);
        if (procFiles.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron archivos proc<i>.txt en " + inDir);
        }

        int nproc = porDefecto(in,
                "Numero de procesos [Enter = detectar por archivos (" + procFiles.size() + ")]: ",
                procFiles.size());

        validarProcFiles(inDir, nproc);

        int totalFrames = enteroPositivo(in, "Numero total de marcos (DEBE ser multiplo de " + nproc + "): ");
        if (totalFrames % nproc != 0) {
            throw new IllegalArgumentException("El numero de marcos (" + totalFrames +
                    ") debe ser MULTIPLO de NPROC (" + nproc + ").");
        }

        Opcion2 opcion2 = new Opcion2();

        boolean ok = opcion2.runOpcion2(nproc, totalFrames, inDir);
        if (!ok) {
            String why = opcional(opcion2.getLastError(), "Fallo en Opcion 2.");
            throw new IllegalArgumentException(why);
        }

        System.out.println("Opcion 2 finalizada.");
    }

    // --------------------------
    // Utilidades de entrada
    // --------------------------
    private int Opciones(Scanner in) {
        System.out.println("""
            ----------------------------
            Seleccione una opcion:
              [1] Opcion 1
              [2] Opcion 2
              [0] Salir
            ----------------------------""");
        System.out.print("Opcion: ");
        String line = in.nextLine().trim();
        if (!line.matches("\\d+")) throw new IllegalArgumentException("Debe ingresar un numero (0,1,2).");
        int val = Integer.parseInt(line);
        if (val < 0 || val > 2) throw new IllegalArgumentException("Opcion fuera de rango.");
        return val;
    }

    private int enteroPositivo(Scanner in, String prompt) {
        System.out.print(prompt);
        String s = in.nextLine().trim();
        if (!s.matches("\\d+")) throw new IllegalArgumentException("Debe ingresar un entero positivo.");
        int v = Integer.parseInt(s);
        if (v <= 0) throw new IllegalArgumentException("El valor debe ser > 0.");
        return v;
    }

    private int porDefecto(Scanner in, String prompt, int def) {
        System.out.print(prompt);
        String s = in.nextLine().trim();
        if (s.isEmpty()) return def;
        if (!s.matches("\\d+")) throw new IllegalArgumentException("Debe ingresar un entero positivo.");
        int v = Integer.parseInt(s);
        if (v <= 0) throw new IllegalArgumentException("El valor debe ser > 0.");
        return v;
    }

    private Path directorio(Scanner in, String prompt) {
        System.out.print(prompt);
        String s = in.nextLine().trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Ruta vacia.");
        Path p = Paths.get(s);
        if (!Files.exists(p)) throw new IllegalArgumentException("Ruta inexistente: " + p);
        if (!Files.isDirectory(p)) throw new IllegalArgumentException("No directorio: " + p);
        return p;
    }

    private Path archivo(Scanner in, String prompt) {
        System.out.print(prompt);
        String s = in.nextLine().trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Ruta vacia.");
        Path p = Paths.get(s);
        if (!Files.exists(p)) throw new IllegalArgumentException("Ruta inexistente: " + p);
        if (!Files.isRegularFile(p)) throw new IllegalArgumentException("No es un archivo regular: " + p);
        return p;
    }

    // Confirmación y/n (acepta también s/n y mayúsculas)
    private boolean Sino(Scanner in, String prompt) {
        System.out.print(prompt);
        String s = in.nextLine().trim().toLowerCase();
        if (s.equals("y") || s.equals("s")) return true;
        if (s.equals("n")) return false;
        throw new IllegalArgumentException("Responda y/n (o s/n).");
    }

    private List<int[]> validarTamanios(String csv, int esperados) {
        if (csv.isEmpty()) throw new IllegalArgumentException("La lista de tamaños no puede estar vacia.");
        String[] parts = csv.split("\\s*,\\s*");
        if (parts.length != esperados) {
            throw new IllegalArgumentException("Se esperaban " + esperados + " tamaños (NFxNC) separados por coma.");
        }
        Pattern pat = Pattern.compile("(?i)^(\\d+)x(\\d+)$");
        List<int[]> res = new ArrayList<>(parts.length);
        for (String px : parts) {
            var m = pat.matcher(px.trim());
            if (!m.matches()) {
                throw new IllegalArgumentException("Formato invalido '" + px + "'. Use NFxNC, p. ej., 8x8.");
            }
            int nf = Integer.parseInt(m.group(1));
            int nc = Integer.parseInt(m.group(2));
            if (nf <= 0 || nc <= 0) {
                throw new IllegalArgumentException("NF y NC deben ser > 0 (dato invalido en '" + px + "').");
            }
            res.add(new int[]{nf, nc});
        }
        return res;
    }

    private void sobrescrituraArchivos(Scanner in, Path outDir) {
        boolean any = !listaProcFiles(outDir).isEmpty();
        if (any) {
            boolean overwrite = Sino(in, "Se encontraron proc<i>.txt en esa carpeta. ¿Sobrescribirlos? (y/n): ");
            if (!overwrite) throw new IllegalArgumentException("Operacion cancelada");
        }
    }

    private List<Path> listaProcFiles(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().matches("proc\\d+\\.txt"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private void validarProcFiles(Path dir, int nproc) {
        // deben existir proc0.txt ... proc{nproc-1}.txt sin faltar ni sobrar
        for (int pid = 0; pid < nproc; pid++) {
            Path f = dir.resolve("proc" + pid + ".txt");
            if (!Files.exists(f)) {
                throw new IllegalArgumentException("Falta el archivo " + f.getFileName() + " en " + dir);
            }
        }
        List<Path> all = listaProcFiles(dir);
        if (all.size() != nproc) {
            throw new IllegalArgumentException("Se encontraron " + all.size() + " archivos proc<i>.txt, pero NPROC=" + nproc + ".");
        }
    }

    private static String opcional(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    // --------------------------
    // Configuración Opción 1
    // --------------------------
    private static final class Config {
        final int tp;
        final int nproc;
        final List<int[]> sizes;
        Config(int tp, int nproc, List<int[]> sizes) {
            this.tp = tp; this.nproc = nproc; this.sizes = sizes;
        }
    }

    private Config leerConfig(Path configPath) {
        Map<String,String> kv = new HashMap<>();
        try {
            for (String raw : Files.readAllLines(configPath)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0 || eq == line.length()-1) {
                    throw new IllegalArgumentException("Linea invalida en config: '" + line + "'. Use CLAVE=VALOR");
                }
                String k = line.substring(0, eq).trim().toUpperCase(Locale.ROOT);
                String v = line.substring(eq+1).trim();
                kv.put(k, v);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo de configuración: " + e.getMessage());
        }

        String tpS   = kv.get("TP");
        String npS   = kv.get("NPROC");
        String tamsS = kv.get("TAMS");
        if (tpS == null || npS == null || tamsS == null) {
            throw new IllegalArgumentException("El archivo de configuración debe contener TP=, NPROC= y TAMS=.");
        }
        if (!tpS.matches("\\d+")) throw new IllegalArgumentException("TP debe ser entero positivo.");
        if (!npS.matches("\\d+")) throw new IllegalArgumentException("NPROC debe ser entero positivo.");
        int tp = Integer.parseInt(tpS);
        int nproc = Integer.parseInt(npS);
        if (tp <= 0 || nproc <= 0) throw new IllegalArgumentException("TP y NPROC deben ser > 0.");

        List<int[]> sizes = validarTamanios(tamsS, nproc);
        return new Config(tp, nproc, sizes);
    }
}