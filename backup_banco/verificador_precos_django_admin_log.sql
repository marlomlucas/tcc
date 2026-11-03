CREATE DATABASE  IF NOT EXISTS `verificador_precos` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `verificador_precos`;
-- MySQL dump 10.13  Distrib 8.0.45, for Win64 (x86_64)
--
-- Host: localhost    Database: verificador_precos
-- ------------------------------------------------------
-- Server version	8.0.45

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `django_admin_log`
--

DROP TABLE IF EXISTS `django_admin_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `django_admin_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `action_time` datetime(6) NOT NULL,
  `object_id` longtext,
  `object_repr` varchar(200) NOT NULL,
  `action_flag` smallint unsigned NOT NULL,
  `change_message` longtext NOT NULL,
  `content_type_id` int DEFAULT NULL,
  `user_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `django_admin_log_content_type_id_c4bce8eb_fk_django_co` (`content_type_id`),
  KEY `django_admin_log_user_id_c564eba6_fk_auth_user_id` (`user_id`),
  CONSTRAINT `django_admin_log_content_type_id_c4bce8eb_fk_django_co` FOREIGN KEY (`content_type_id`) REFERENCES `django_content_type` (`id`),
  CONSTRAINT `django_admin_log_user_id_c564eba6_fk_auth_user_id` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`id`),
  CONSTRAINT `django_admin_log_chk_1` CHECK ((`action_flag` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=53 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `django_admin_log`
--

LOCK TABLES `django_admin_log` WRITE;
/*!40000 ALTER TABLE `django_admin_log` DISABLE KEYS */;
INSERT INTO `django_admin_log` VALUES (1,'2026-06-24 18:52:48.506044','1','Arroz Tipo 1 - R$ 24.90',1,'[{\"added\": {}}]',7,1),(2,'2026-10-02 19:45:06.264723','2','Chá de Camomila Madrugada - R$ 2.49',1,'[{\"added\": {}}]',7,1),(3,'2026-10-06 14:33:10.239790','3','Teste á é @ ñ ; \' \" , . : ´ ` { } [ ] ( ) < > _ - | \\ / ! # $ % - R$ 1.99',1,'[{\"added\": {}}]',7,1),(4,'2026-10-06 14:51:41.374663','3','Teste á é @ ñ ; \' \" , . : ´ ` { } [ ] ( ) < > _ - | \\ / ! # $ % - R$ 1.99',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(5,'2026-10-06 14:53:33.723836','3','Teste á é @ ñ ; \' \" , . : ´ ` { } [ ] ( ) < > _ - | \\ / ! # $ % - R$ 1.99',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(6,'2026-10-12 21:20:05.901392','4','Chá de Camomila Gengibre e Mel Barão - R$ 2.99',1,'[{\"added\": {}}]',7,1),(7,'2026-10-13 15:23:57.728894','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',2,'[{\"changed\": {\"fields\": [\"Nome\", \"Descri\\u00e7\\u00e3o\", \"Imagem do Produto\"]}}]',7,1),(8,'2026-10-13 15:27:03.865355','4','Chá de Camomila Gengibre e Mel Barão - R$ 2.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(9,'2026-10-13 15:27:54.097412','4','Chá de Camomila Gengibre e Mel Barão - R$ 2.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(10,'2026-10-13 18:19:10.460641','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(11,'2026-10-13 19:08:29.163196','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(12,'2026-10-13 19:08:39.050867','4','Chá de Camomila Gengibre e Mel Barão - R$ 2.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(13,'2026-10-13 19:08:45.980384','2','Chá de Camomila Madrugada - R$ 2.49',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(14,'2026-10-16 21:48:11.964141','5','Teste1 aaaaaa - R$ 1.99',1,'[{\"added\": {}}]',7,1),(15,'2026-10-16 21:57:11.239528','5','Teste1 aaaaaa - R$ 1.99',3,'',7,1),(16,'2026-10-16 21:57:38.032795','6','Teste1 aaaaaa - R$ 1.99',1,'[{\"added\": {}}]',7,1),(17,'2026-10-16 21:59:17.606400','6','Teste1 aaaaaa - R$ 1.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(18,'2026-10-16 22:00:04.770450','6','Teste1 - R$ 1.99',2,'[{\"changed\": {\"fields\": [\"Nome\"]}}]',7,1),(19,'2026-10-16 22:00:48.781792','6','Teste1 - R$ 1.99',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(20,'2026-10-16 22:01:19.755089','6','Teste1 - R$ 1.99',3,'',7,1),(21,'2026-10-16 22:10:37.755587','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(22,'2026-10-16 22:10:48.936921','4','Chá de Camomila Gengibre e Mel Barão - R$ 2.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(23,'2026-10-16 22:11:00.041342','2','Chá de Camomila Madrugada - R$ 2.49',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(24,'2026-10-16 22:11:10.352284','3','Teste á é @ ñ ; \' \" , . : ´ ` { } [ ] ( ) < > _ - | \\ / ! # $ % - R$ 1.99',2,'[]',7,1),(25,'2026-10-16 22:16:12.921801','4','Chá de Camomila Gengibre e Mel Barão - R$ 2.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(26,'2026-10-16 22:19:58.858281','4','Chá de Camomila Gengibre e Mel Barão - R$ 2.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(27,'2026-10-17 16:34:15.630670','7','TesteAAAAAA - R$ 999.99',1,'[{\"added\": {}}]',7,1),(28,'2026-10-17 16:35:38.208532','7','TesteAAAAAA - R$ 999.99',3,'',7,1),(29,'2026-10-17 16:37:07.203301','8','aaaaaaaaa - R$ 222',1,'[{\"added\": {}}]',7,1),(30,'2026-10-17 16:37:38.192474','8','aaaaaaaaa - R$ 222.00',3,'',7,1),(31,'2026-10-20 19:15:51.975519','9','sasas - R$ 1.99',1,'[{\"added\": {}}]',7,1),(32,'2026-10-20 19:17:43.207821','9','sasas - R$ 1.99',3,'',7,1),(33,'2026-10-20 19:18:15.399031','10','sdsds - R$ 11',1,'[{\"added\": {}}]',7,1),(34,'2026-10-20 19:18:30.370847','10','sdsds - R$ 11.00',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(35,'2026-10-20 19:19:13.880533','10','sdsds - R$ 11.00',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(36,'2026-10-20 19:20:09.913402','10','sdsds - R$ 11.00',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(37,'2026-10-20 19:20:18.819006','10','sdsds - R$ 11.00',3,'',7,1),(38,'2026-10-20 19:51:41.765607','11','xsxsxsxsx - R$ 11',1,'[{\"added\": {}}]',7,1),(39,'2026-10-20 19:51:56.978107','11','xsxsxsxsx - R$ 11.00',3,'',7,1),(40,'2026-10-20 20:03:09.558424','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',2,'[]',7,1),(41,'2026-10-20 20:04:45.861018','12','qqqqqqqqqq - R$ 11',1,'[{\"added\": {}}]',7,1),(42,'2026-10-20 20:04:52.645184','12','qqqqqqqqqq - R$ 11.00',3,'',7,1),(43,'2026-10-20 20:05:21.741041','13','aaaaaaaaaaa - R$ 22',1,'[{\"added\": {}}]',7,1),(44,'2026-10-20 20:05:42.638122','13','aaaaaaaaaaa - R$ 22.00',3,'',7,1),(45,'2026-10-20 20:07:05.290469','14','zasa - R$ 2',1,'[{\"added\": {}}]',7,1),(46,'2026-10-20 20:07:24.856331','14','ddvdvdv - R$ 2.00',2,'[{\"changed\": {\"fields\": [\"Nome\"]}}]',7,1),(47,'2026-10-20 20:07:28.614176','14','ddvdvdv - R$ 2.00',3,'',7,1),(48,'2026-10-20 20:07:41.117570','15','sddsdsds - R$ 2',1,'[{\"added\": {}}]',7,1),(49,'2026-10-20 20:07:46.878658','15','dwwdwd - R$ 2.00',2,'[{\"changed\": {\"fields\": [\"Nome\"]}}]',7,1),(50,'2026-10-20 20:07:51.012790','15','dwwdwd - R$ 2.00',3,'',7,1),(51,'2026-10-20 20:08:25.335074','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(52,'2026-10-20 20:08:32.557144','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1);
/*!40000 ALTER TABLE `django_admin_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 13:12:37
