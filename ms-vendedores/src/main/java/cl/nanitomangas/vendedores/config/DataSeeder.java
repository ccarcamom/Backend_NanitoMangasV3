package cl.nanitomangas.vendedores.config;

import cl.nanitomangas.vendedores.model.Producto;
import cl.nanitomangas.vendedores.repository.ProductoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** Siembra los 17 productos de src/data/seed.js del frontend si la colección está vacía. */
@Component
public class DataSeeder implements CommandLineRunner {

    private final ProductoRepository repo;
    private final String sellerId;

    public DataSeeder(ProductoRepository repo, @Value("${seed.seller-id}") String sellerId) {
        this.repo = repo;
        this.sellerId = sellerId;
    }

    @Override
    public void run(String... args) {
        if (repo.count() > 0) return;
        List<Producto> productos = List.of(
            p("manga-1", "One Piece - Tomo 1", "Ivrea", "mangas", "shonen", 10990, null, 12, "/img/OnePiece_1_IVREAAR.jpg", "Luffy se lanza al mar para convertirse en el Rey de los Piratas.", true, true, false),
            p("manga-2", "Vagabond - Tomo 1", "Panini Manga", "mangas", "seinen", 12990, 9990, 8, "/img/Vagabond_1_IVREAAR.jpg", "La historia de Miyamoto Musashi y su camino de la espada.", false, true, true),
            p("manga-3", "Chainsaw Man - Tomo 1", "Norma", "mangas", "shonen", 9990, null, 0, "/img/ChainsawMan_1_IVREAAR.jpg", "Denji, un joven endeudado, se fusiona con su demonio motosierra.", true, false, true),
            p("manga-4", "20th Century Boys - Tomo 1", "Planeta Cómic", "mangas", "seinen", 14990, null, 5, "/img/20thCenturyBoys_1_PlanetaComic.jpg", "Un grupo de amigos de la infancia enfrenta una misteriosa secta.", false, true, false),
            p("manga-5", "Atelier of Witch Hat - Tomo 1", "Milky Way", "mangas", "seinen", 9500, null, 20, "/img/AtelierOfWitchHat_1_MILKYWAY.jpg", "Coco sueña con ser maga y entra en un mundo de magia oculta.", true, false, false),
            p("manga-6", "Dandadan - Tomo 1", "Ivrea", "mangas", "shonen", 12990, 10990, 7, "/img/Dandadan_1_IVREAAR.jpg", "Fantasmas y alienígenas se cruzan en la vida de dos estudiantes.", false, false, true),
            p("manga-7", "Buenas Noches Punpun - Tomo 1", "Ivrea", "mangas", "seinen", 10990, null, 6, "/img/BuenasNochesPunpun_IVREAAR.jpg", "Punpun crece entre la inocencia y la crudeza de la adolescencia.", true, false, false),
            p("manga-8", "Dorohedoro - Tomo 1", "Panini Manga", "mangas", "seinen", 12990, null, 6, "/img/Dorohedoro_1_PANINI.jpg", "Caiman, con cabeza de reptil y sin memoria, busca al mago que lo transformó.", false, false, false),
            p("manga-9", "Los Diarios de la Boticaria - Tomo 1", "Panini Manga", "mangas", "seinen", 9990, null, 8, "/img/LosDiariosDeLaBoticaria_1_PANINI.jpg", "Maomao resuelve misterios con sus conocimientos de boticaria en el palacio.", false, false, false),
            p("funko-1", "Funko Pop Brook", "One Piece", "funko", null, 19990, null, 6, "/img/brook_funko.webp", "Funko Pop de Brook, el músico de los Sombrero de Paja.", true, true, false),
            p("funko-2", "Funko Pop Luffy", "One Piece", "funko", null, 21990, 17990, 4, "/img/luffy_funko.webp", "Funko Pop de Monkey D. Luffy, capitán de los Sombrero de Paja.", false, true, true),
            p("funko-3", "Funko Pop Nami", "One Piece", "funko", null, 19990, null, 9, "/img/nami_funko.webp", "Funko Pop de Nami, la navegante de los Sombrero de Paja.", true, false, false),
            p("funko-4", "Funko Pop Sanji", "One Piece", "funko", null, 24990, null, 3, "/img/sanji_funko.webp", "Funko Pop de Sanji, el cocinero de los Sombrero de Paja.", false, false, true),
            p("acc-1", "Figura Ace 18 cm", "One Piece", "figuras", null, 34990, null, 2, "/img/ace.webp", "Figura de Portgas D. Ace de 18 cm de alto.", true, true, false),
            p("acc-2", "Figura Luffy 18 cm", "One Piece", "figuras", null, 29990, null, 5, "/img/luffy.webp", "Figura de Monkey D. Luffy de 18 cm de alto.", false, true, false),
            p("acc-3", "Aretes de Zoro", "One Piece", "accesorios", null, 12990, null, 15, "/img/aretes_zoro.webp", "Aretes inspirados en los pendientes de Roronoa Zoro.", false, false, true),
            p("acc-4", "Moledor Nika", "One Piece", "accesorios", null, 14990, null, 10, "/img/fruta_moledor.webp", "Moledor inspirado en la fruta de Luffy en modo Nika.", true, false, true)

        );
        productos.forEach(p -> p.sellerId = sellerId);
        repo.saveAll(productos);
    }

    private static Producto p(String id, String name, String brand, String categoryId, String genre, Integer price,
                              Integer salePrice, Integer stock, String image, String description,
                              Boolean isNew, Boolean isBestSeller, Boolean isRestock) {
        Producto p = new Producto();
        p.id = id; p.name = name; p.brand = brand; p.categoryId = categoryId; p.genre = genre;
        p.price = price; p.salePrice = salePrice; p.stock = stock; p.image = image; p.description = description;
        p.isNew = isNew; p.isBestSeller = isBestSeller; p.isRestock = isRestock;
        return p;
    }
}
