-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: verificador_precos
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `verificador_precos`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `verificador_precos` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `verificador_precos`;

--
-- Table structure for table `auth_group`
--

DROP TABLE IF EXISTS `auth_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_group` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(150) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_group`
--

LOCK TABLES `auth_group` WRITE;
/*!40000 ALTER TABLE `auth_group` DISABLE KEYS */;
/*!40000 ALTER TABLE `auth_group` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_group_permissions`
--

DROP TABLE IF EXISTS `auth_group_permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_group_permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` int NOT NULL,
  `permission_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `auth_group_permissions_group_id_permission_id_0cd325b0_uniq` (`group_id`,`permission_id`),
  KEY `auth_group_permissio_permission_id_84c5c92e_fk_auth_perm` (`permission_id`),
  CONSTRAINT `auth_group_permissio_permission_id_84c5c92e_fk_auth_perm` FOREIGN KEY (`permission_id`) REFERENCES `auth_permission` (`id`),
  CONSTRAINT `auth_group_permissions_group_id_b120cbf9_fk_auth_group_id` FOREIGN KEY (`group_id`) REFERENCES `auth_group` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_group_permissions`
--

LOCK TABLES `auth_group_permissions` WRITE;
/*!40000 ALTER TABLE `auth_group_permissions` DISABLE KEYS */;
/*!40000 ALTER TABLE `auth_group_permissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_permission`
--

DROP TABLE IF EXISTS `auth_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_permission` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `content_type_id` int NOT NULL,
  `codename` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `auth_permission_content_type_id_codename_01ab375a_uniq` (`content_type_id`,`codename`),
  CONSTRAINT `auth_permission_content_type_id_2f476e4b_fk_django_co` FOREIGN KEY (`content_type_id`) REFERENCES `django_content_type` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_permission`
--

LOCK TABLES `auth_permission` WRITE;
/*!40000 ALTER TABLE `auth_permission` DISABLE KEYS */;
INSERT INTO `auth_permission` VALUES (1,'Can add log entry',1,'add_logentry'),(2,'Can change log entry',1,'change_logentry'),(3,'Can delete log entry',1,'delete_logentry'),(4,'Can view log entry',1,'view_logentry'),(5,'Can add permission',3,'add_permission'),(6,'Can change permission',3,'change_permission'),(7,'Can delete permission',3,'delete_permission'),(8,'Can view permission',3,'view_permission'),(9,'Can add group',2,'add_group'),(10,'Can change group',2,'change_group'),(11,'Can delete group',2,'delete_group'),(12,'Can view group',2,'view_group'),(13,'Can add user',4,'add_user'),(14,'Can change user',4,'change_user'),(15,'Can delete user',4,'delete_user'),(16,'Can view user',4,'view_user'),(17,'Can add content type',5,'add_contenttype'),(18,'Can change content type',5,'change_contenttype'),(19,'Can delete content type',5,'delete_contenttype'),(20,'Can view content type',5,'view_contenttype'),(21,'Can add session',6,'add_session'),(22,'Can change session',6,'change_session'),(23,'Can delete session',6,'delete_session'),(24,'Can view session',6,'view_session'),(25,'Can add Produto',7,'add_produto'),(26,'Can change Produto',7,'change_produto'),(27,'Can delete Produto',7,'delete_produto'),(28,'Can view Produto',7,'view_produto');
/*!40000 ALTER TABLE `auth_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_user`
--

DROP TABLE IF EXISTS `auth_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `password` varchar(128) NOT NULL,
  `last_login` datetime(6) DEFAULT NULL,
  `is_superuser` tinyint(1) NOT NULL,
  `username` varchar(150) NOT NULL,
  `first_name` varchar(150) NOT NULL,
  `last_name` varchar(150) NOT NULL,
  `email` varchar(254) NOT NULL,
  `is_staff` tinyint(1) NOT NULL,
  `is_active` tinyint(1) NOT NULL,
  `date_joined` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_user`
--

LOCK TABLES `auth_user` WRITE;
/*!40000 ALTER TABLE `auth_user` DISABLE KEYS */;
INSERT INTO `auth_user` VALUES (1,'pbkdf2_sha256$1200000$GyeQp5V48S1dNehE7mHVND$g4My4KWmlDtb7GEYa9kZQbYniiQYAt8ay4LLtAHcS8c=','2026-11-03 21:33:12.832172',1,'admin','','','admin@atl.com',1,1,'2026-06-22 16:00:07.452642');
/*!40000 ALTER TABLE `auth_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_user_groups`
--

DROP TABLE IF EXISTS `auth_user_groups`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_user_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `group_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `auth_user_groups_user_id_group_id_94350c0c_uniq` (`user_id`,`group_id`),
  KEY `auth_user_groups_group_id_97559544_fk_auth_group_id` (`group_id`),
  CONSTRAINT `auth_user_groups_group_id_97559544_fk_auth_group_id` FOREIGN KEY (`group_id`) REFERENCES `auth_group` (`id`),
  CONSTRAINT `auth_user_groups_user_id_6a12ed8b_fk_auth_user_id` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_user_groups`
--

LOCK TABLES `auth_user_groups` WRITE;
/*!40000 ALTER TABLE `auth_user_groups` DISABLE KEYS */;
/*!40000 ALTER TABLE `auth_user_groups` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_user_user_permissions`
--

DROP TABLE IF EXISTS `auth_user_user_permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_user_user_permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `permission_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `auth_user_user_permissions_user_id_permission_id_14a6b632_uniq` (`user_id`,`permission_id`),
  KEY `auth_user_user_permi_permission_id_1fbb5f2c_fk_auth_perm` (`permission_id`),
  CONSTRAINT `auth_user_user_permi_permission_id_1fbb5f2c_fk_auth_perm` FOREIGN KEY (`permission_id`) REFERENCES `auth_permission` (`id`),
  CONSTRAINT `auth_user_user_permissions_user_id_a95ead1b_fk_auth_user_id` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_user_user_permissions`
--

LOCK TABLES `auth_user_user_permissions` WRITE;
/*!40000 ALTER TABLE `auth_user_user_permissions` DISABLE KEYS */;
/*!40000 ALTER TABLE `auth_user_user_permissions` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB AUTO_INCREMENT=89 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `django_admin_log`
--

LOCK TABLES `django_admin_log` WRITE;
/*!40000 ALTER TABLE `django_admin_log` DISABLE KEYS */;
INSERT INTO `django_admin_log` VALUES (1,'2026-06-24 18:52:48.506044','1','Arroz Tipo 1 - R$ 24.90',1,'[{\"added\": {}}]',7,1),(2,'2026-10-02 19:45:06.264723','2','Chá de Camomila Madrugada - R$ 2.49',1,'[{\"added\": {}}]',7,1),(3,'2026-10-06 14:33:10.239790','3','Teste á é @ ñ ; \' \" , . : ´ ` { } [ ] ( ) < > _ - | \\ / ! # $ % - R$ 1.99',1,'[{\"added\": {}}]',7,1),(4,'2026-10-06 14:51:41.374663','3','Teste á é @ ñ ; \' \" , . : ´ ` { } [ ] ( ) < > _ - | \\ / ! # $ % - R$ 1.99',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(5,'2026-10-06 14:53:33.723836','3','Teste á é @ ñ ; \' \" , . : ´ ` { } [ ] ( ) < > _ - | \\ / ! # $ % - R$ 1.99',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(6,'2026-10-12 21:20:05.901392','4','Chá de Camomila Gengibre e Mel Barão - R$ 2.99',1,'[{\"added\": {}}]',7,1),(7,'2026-10-13 15:23:57.728894','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',2,'[{\"changed\": {\"fields\": [\"Nome\", \"Descri\\u00e7\\u00e3o\", \"Imagem do Produto\"]}}]',7,1),(8,'2026-10-13 15:27:03.865355','4','Chá de Camomila Gengibre e Mel Barão - R$ 2.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(9,'2026-10-13 15:27:54.097412','4','Chá de Camomila Gengibre e Mel Barão - R$ 2.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(10,'2026-10-13 18:19:10.460641','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(11,'2026-10-13 19:08:29.163196','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(12,'2026-10-13 19:08:39.050867','4','Chá de Camomila Gengibre e Mel Barão - R$ 2.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(13,'2026-10-13 19:08:45.980384','2','Chá de Camomila Madrugada - R$ 2.49',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(14,'2026-10-16 21:48:11.964141','5','Teste1 aaaaaa - R$ 1.99',1,'[{\"added\": {}}]',7,1),(15,'2026-10-16 21:57:11.239528','5','Teste1 aaaaaa - R$ 1.99',3,'',7,1),(16,'2026-10-16 21:57:38.032795','6','Teste1 aaaaaa - R$ 1.99',1,'[{\"added\": {}}]',7,1),(17,'2026-10-16 21:59:17.606400','6','Teste1 aaaaaa - R$ 1.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(18,'2026-10-16 22:00:04.770450','6','Teste1 - R$ 1.99',2,'[{\"changed\": {\"fields\": [\"Nome\"]}}]',7,1),(19,'2026-10-16 22:00:48.781792','6','Teste1 - R$ 1.99',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(20,'2026-10-16 22:01:19.755089','6','Teste1 - R$ 1.99',3,'',7,1),(21,'2026-10-16 22:10:37.755587','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(22,'2026-10-16 22:10:48.936921','4','Chá de Camomila Gengibre e Mel Barão - R$ 2.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(23,'2026-10-16 22:11:00.041342','2','Chá de Camomila Madrugada - R$ 2.49',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(24,'2026-10-16 22:11:10.352284','3','Teste á é @ ñ ; \' \" , . : ´ ` { } [ ] ( ) < > _ - | \\ / ! # $ % - R$ 1.99',2,'[]',7,1),(25,'2026-10-16 22:16:12.921801','4','Chá de Camomila Gengibre e Mel Barão - R$ 2.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(26,'2026-10-16 22:19:58.858281','4','Chá de Camomila Gengibre e Mel Barão - R$ 2.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(27,'2026-10-17 16:34:15.630670','7','TesteAAAAAA - R$ 999.99',1,'[{\"added\": {}}]',7,1),(28,'2026-10-17 16:35:38.208532','7','TesteAAAAAA - R$ 999.99',3,'',7,1),(29,'2026-10-17 16:37:07.203301','8','aaaaaaaaa - R$ 222',1,'[{\"added\": {}}]',7,1),(30,'2026-10-17 16:37:38.192474','8','aaaaaaaaa - R$ 222.00',3,'',7,1),(31,'2026-10-20 19:15:51.975519','9','sasas - R$ 1.99',1,'[{\"added\": {}}]',7,1),(32,'2026-10-20 19:17:43.207821','9','sasas - R$ 1.99',3,'',7,1),(33,'2026-10-20 19:18:15.399031','10','sdsds - R$ 11',1,'[{\"added\": {}}]',7,1),(34,'2026-10-20 19:18:30.370847','10','sdsds - R$ 11.00',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(35,'2026-10-20 19:19:13.880533','10','sdsds - R$ 11.00',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(36,'2026-10-20 19:20:09.913402','10','sdsds - R$ 11.00',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(37,'2026-10-20 19:20:18.819006','10','sdsds - R$ 11.00',3,'',7,1),(38,'2026-10-20 19:51:41.765607','11','xsxsxsxsx - R$ 11',1,'[{\"added\": {}}]',7,1),(39,'2026-10-20 19:51:56.978107','11','xsxsxsxsx - R$ 11.00',3,'',7,1),(40,'2026-10-20 20:03:09.558424','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',2,'[]',7,1),(41,'2026-10-20 20:04:45.861018','12','qqqqqqqqqq - R$ 11',1,'[{\"added\": {}}]',7,1),(42,'2026-10-20 20:04:52.645184','12','qqqqqqqqqq - R$ 11.00',3,'',7,1),(43,'2026-10-20 20:05:21.741041','13','aaaaaaaaaaa - R$ 22',1,'[{\"added\": {}}]',7,1),(44,'2026-10-20 20:05:42.638122','13','aaaaaaaaaaa - R$ 22.00',3,'',7,1),(45,'2026-10-20 20:07:05.290469','14','zasa - R$ 2',1,'[{\"added\": {}}]',7,1),(46,'2026-10-20 20:07:24.856331','14','ddvdvdv - R$ 2.00',2,'[{\"changed\": {\"fields\": [\"Nome\"]}}]',7,1),(47,'2026-10-20 20:07:28.614176','14','ddvdvdv - R$ 2.00',3,'',7,1),(48,'2026-10-20 20:07:41.117570','15','sddsdsds - R$ 2',1,'[{\"added\": {}}]',7,1),(49,'2026-10-20 20:07:46.878658','15','dwwdwd - R$ 2.00',2,'[{\"changed\": {\"fields\": [\"Nome\"]}}]',7,1),(50,'2026-10-20 20:07:51.012790','15','dwwdwd - R$ 2.00',3,'',7,1),(51,'2026-10-20 20:08:25.335074','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(52,'2026-10-20 20:08:32.557144','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(53,'2026-11-03 21:22:56.272824','1','Arroz branco Namorado 5kg Tipo 1 - R$ 24.90',3,'',7,1),(54,'2026-11-03 21:23:59.990763','16','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',1,'[{\"added\": {}}]',7,1),(55,'2026-11-03 21:24:14.576370','16','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',3,'',7,1),(56,'2026-11-03 21:26:06.506646','17','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',1,'[{\"added\": {}}]',7,1),(57,'2026-11-03 21:26:21.484186','17','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',3,'',7,1),(58,'2026-11-03 21:26:43.218111','18','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',1,'[{\"added\": {}}]',7,1),(59,'2026-11-03 21:27:30.853240','18','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',3,'',7,1),(60,'2026-11-03 21:27:52.402554','19','Arroz branco Namorado 5kg Tipo 1 - R$ 123',1,'[{\"added\": {}}]',7,1),(61,'2026-11-03 21:28:21.580523','19','Arroz branco Namorado 5kg Tipo 1 - R$ 123.00',3,'',7,1),(62,'2026-11-03 21:29:04.265843','20','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',1,'[{\"added\": {}}]',7,1),(63,'2026-11-03 22:50:13.090011','20','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',3,'',7,1),(64,'2026-11-03 22:51:07.585934','21','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',1,'[{\"added\": {}}]',7,1),(65,'2026-11-03 22:54:19.163017','21','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(66,'2026-11-03 22:55:06.811955','21','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(67,'2026-11-03 22:56:27.962127','21','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(68,'2026-11-03 22:57:16.859599','21','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(69,'2026-11-03 22:59:45.391657','21','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',2,'[{\"changed\": {\"fields\": [\"Descri\\u00e7\\u00e3o\"]}}]',7,1),(70,'2026-11-03 22:59:58.820765','21','aaaaaaaaaaaaaaaaaaaaa - R$ 24.99',2,'[{\"changed\": {\"fields\": [\"Nome\", \"Descri\\u00e7\\u00e3o\"]}}]',7,1),(71,'2026-11-03 23:00:08.532701','21','aaaaaaaaaaaaaaaaaaaaa - R$ 11',2,'[{\"changed\": {\"fields\": [\"Pre\\u00e7o\"]}}]',7,1),(72,'2026-11-03 23:00:30.089290','21','Arroz branco Namorado 5kg Tipo 1 - R$ 24.99',2,'[{\"changed\": {\"fields\": [\"Nome\", \"Descri\\u00e7\\u00e3o\", \"Pre\\u00e7o\"]}}]',7,1),(73,'2026-11-05 21:04:03.294335','22','sdsds - R$ 22',1,'[{\"added\": {}}]',7,1),(74,'2026-11-05 21:04:45.357009','22','sdsds - R$ 22.00',3,'',7,1),(75,'2026-11-09 18:30:56.915506','23','TESTE_TCC_A - R$ 10',1,'[{\"added\": {}}]',7,1),(76,'2026-11-09 18:33:32.834061','24','TESTE_TCC_B - R$ 5',1,'[{\"added\": {}}]',7,1),(77,'2026-11-09 18:36:04.537672','24','TESTE_TCC_B - R$ 5.00',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(78,'2026-11-09 18:39:11.495873','24','TESTE_TCC_B - R$ 5.00',2,'[]',7,1),(79,'2026-11-09 18:39:14.002400','24','TESTE_TCC_B - R$ 5.00',2,'[]',7,1),(80,'2026-11-09 18:40:25.936648','24','TESTE_TCC_B - R$ 5.00',3,'',7,1),(81,'2026-11-09 18:40:46.479799','25','TESTE_TCC_B - R$ 5',1,'[{\"added\": {}}]',7,1),(82,'2026-11-09 18:41:16.365205','23','TESTE_TCC_A - R$ 12.50',2,'[{\"changed\": {\"fields\": [\"Pre\\u00e7o\"]}}]',7,1),(83,'2026-11-09 18:41:58.419541','23','TESTE_TCC_A - R$ 12.50',2,'[{\"changed\": {\"fields\": [\"Imagem do Produto\"]}}]',7,1),(84,'2026-11-09 18:43:03.744147','23','TESTE_TCC_A - R$ 12.50',2,'[{\"changed\": {\"fields\": [\"C\\u00f3digo de Barras\"]}}]',7,1),(85,'2026-11-09 18:44:07.698699','23','TESTE_TCC_A - R$ 12.50',3,'',7,1),(86,'2026-11-09 18:44:52.748120','25','Coca-Cola 000 - R$ 6',2,'[{\"changed\": {\"fields\": [\"Nome\", \"Pre\\u00e7o\", \"Imagem do Produto\"]}}]',7,1),(87,'2026-11-09 22:06:57.510095','25','Coca Zero Cafeína - R$ 6.00',2,'[{\"changed\": {\"fields\": [\"Nome\"]}}]',7,1),(88,'2026-11-09 22:07:41.225168','25','Coca Zero açúcar Zero Cafeína - R$ 6.59',2,'[{\"changed\": {\"fields\": [\"Nome\", \"Pre\\u00e7o\"]}}]',7,1);
/*!40000 ALTER TABLE `django_admin_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `django_content_type`
--

DROP TABLE IF EXISTS `django_content_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `django_content_type` (
  `id` int NOT NULL AUTO_INCREMENT,
  `app_label` varchar(100) NOT NULL,
  `model` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `django_content_type_app_label_model_76bd3d3b_uniq` (`app_label`,`model`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `django_content_type`
--

LOCK TABLES `django_content_type` WRITE;
/*!40000 ALTER TABLE `django_content_type` DISABLE KEYS */;
INSERT INTO `django_content_type` VALUES (1,'admin','logentry'),(2,'auth','group'),(3,'auth','permission'),(4,'auth','user'),(5,'contenttypes','contenttype'),(7,'produtos','produto'),(6,'sessions','session');
/*!40000 ALTER TABLE `django_content_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `django_migrations`
--

DROP TABLE IF EXISTS `django_migrations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `django_migrations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `app` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `applied` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `django_migrations`
--

LOCK TABLES `django_migrations` WRITE;
/*!40000 ALTER TABLE `django_migrations` DISABLE KEYS */;
INSERT INTO `django_migrations` VALUES (1,'contenttypes','0001_initial','2026-02-14 23:18:40.204175'),(2,'auth','0001_initial','2026-02-14 23:18:40.903222'),(3,'admin','0001_initial','2026-02-14 23:18:41.071269'),(4,'admin','0002_logentry_remove_auto_add','2026-02-14 23:18:41.078890'),(5,'admin','0003_logentry_add_action_flag_choices','2026-02-14 23:18:41.090134'),(6,'contenttypes','0002_remove_content_type_name','2026-02-14 23:18:41.219780'),(7,'auth','0002_alter_permission_name_max_length','2026-02-14 23:18:41.284047'),(8,'auth','0003_alter_user_email_max_length','2026-02-14 23:18:41.308492'),(9,'auth','0004_alter_user_username_opts','2026-02-14 23:18:41.317475'),(10,'auth','0005_alter_user_last_login_null','2026-02-14 23:18:41.382901'),(11,'auth','0006_require_contenttypes_0002','2026-02-14 23:18:41.386686'),(12,'auth','0007_alter_validators_add_error_messages','2026-02-14 23:18:41.394027'),(13,'auth','0008_alter_user_username_max_length','2026-02-14 23:18:41.468547'),(14,'auth','0009_alter_user_last_name_max_length','2026-02-14 23:18:41.535600'),(15,'auth','0010_alter_group_name_max_length','2026-02-14 23:18:41.558425'),(16,'auth','0011_update_proxy_permissions','2026-02-14 23:18:41.567598'),(17,'auth','0012_alter_user_first_name_max_length','2026-02-14 23:18:41.634625'),(18,'sessions','0001_initial','2026-02-14 23:18:41.676183'),(19,'produtos','0001_initial','2026-06-22 15:15:36.252205'),(20,'produtos','0002_produto_imagem','2026-10-13 14:58:13.695247'),(21,'produtos','0003_remove_produto_imagem','2026-10-16 22:29:57.032753');
/*!40000 ALTER TABLE `django_migrations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `django_session`
--

DROP TABLE IF EXISTS `django_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `django_session` (
  `session_key` varchar(40) NOT NULL,
  `session_data` longtext NOT NULL,
  `expire_date` datetime(6) NOT NULL,
  PRIMARY KEY (`session_key`),
  KEY `django_session_expire_date_a5c62663` (`expire_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `django_session`
--

LOCK TABLES `django_session` WRITE;
/*!40000 ALTER TABLE `django_session` DISABLE KEYS */;
INSERT INTO `django_session` VALUES ('d33sllyd981q3gegjgdvo2dnuv2ja5ul','.eJxVjLsOwjAMAP_FM4qSOMFtR_Z-Q-TGDimgVupjQvw7qtQB1rvTvSHxvtW0r7qkUaADB5dfNnB-6nQIefB0n02ep20ZB3Mk5rSr6WfR1-1s_waV1wodYCjE2Q1UmNQ2OWrrJObQIjIWjEXRNvZKSCQeNTqxSJSDL-IpKsHnC-Q7N3w:1xOM80:1DiII5xtD4Nm95W_67h3eDlmSepyb7t8hfyO0Bur5CQ','2026-11-17 21:33:12.835183'),('ez0qu4ak3hh95lhcwkhisrmaw5raudks','.eJxVjLsOwjAMAP_FM4qSOMFtR_Z-Q-TGDimgVupjQvw7qtQB1rvTvSHxvtW0r7qkUaADB5dfNnB-6nQIefB0n02ep20ZB3Mk5rSr6WfR1-1s_waV1wodYCjE2Q1UmNQ2OWrrJObQIjIWjEXRNvZKSCQeNTqxSJSDL-IpKsHnC-Q7N3w:1xOM6E:hrOdmuqoWkfjuSv_g6i9XYDiuVTIlSMTDjSk3eRFNsY','2026-11-17 21:31:22.787575'),('n3wnq3pq31gahj0wz68ml0v9qredf2ja','.eJxVjLsOwjAMAP_FM4qSOMFtR_Z-Q-TGDimgVupjQvw7qtQB1rvTvSHxvtW0r7qkUaADB5dfNnB-6nQIefB0n02ep20ZB3Mk5rSr6WfR1-1s_waV1wodYCjE2Q1UmNQ2OWrrJObQIjIWjEXRNvZKSCQeNTqxSJSDL-IpKsHnC-Q7N3w:1xJFHP:dy2AwnCFhtV2RqT42GLE3rEj7eUmzJ4DktQG36umGzU','2026-11-03 19:13:47.585794'),('q4v9aw63iq4l3pii0swi0eemj3ngsfoh','.eJxVjLsOwjAMAP_FM4qSOMFtR_Z-Q-TGDimgVupjQvw7qtQB1rvTvSHxvtW0r7qkUaADB5dfNnB-6nQIefB0n02ep20ZB3Mk5rSr6WfR1-1s_waV1wodYCjE2Q1UmNQ2OWrrJObQIjIWjEXRNvZKSCQeNTqxSJSDL-IpKsHnC-Q7N3w:1wh823:g2i8nGsUuMN-IBnvHCSlQjfO6GDt5h9VT61ok47XSMY','2026-07-21 15:48:23.397037'),('vj3zappsl8emwkwk86oxuo0wgyvtqvtu','.eJxVjLsOwjAMAP_FM4qSOMFtR_Z-Q-TGDimgVupjQvw7qtQB1rvTvSHxvtW0r7qkUaADB5dfNnB-6nQIefB0n02ep20ZB3Mk5rSr6WfR1-1s_waV1wodYCjE2Q1UmNQ2OWrrJObQIjIWjEXRNvZKSCQeNTqxSJSDL-IpKsHnC-Q7N3w:1xOHnR:f85k3zVuA014f0X5tyCgcWb7FF_t2S656M21YjM2MI0','2026-11-17 16:55:41.603541'),('xaw9n5doodfe74n5fhctnzrae28pt3el','.eJxVjLsOwjAMAP_FM4qSOMFtR_Z-Q-TGDimgVupjQvw7qtQB1rvTvSHxvtW0r7qkUaADB5dfNnB-6nQIefB0n02ep20ZB3Mk5rSr6WfR1-1s_waV1wodYCjE2Q1UmNQ2OWrrJObQIjIWjEXRNvZKSCQeNTqxSJSDL-IpKsHnC-Q7N3w:1xE6BY:mFd_1swGCQv80jpy_TnJXRvhJbwoyA0cxGkFpz89Eas','2026-10-20 14:30:28.738971');
/*!40000 ALTER TABLE `django_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `produtos`
--

DROP TABLE IF EXISTS `produtos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `produtos` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `codigo_barra` varchar(20) NOT NULL,
  `nome` varchar(150) NOT NULL,
  `descricao` longtext NOT NULL,
  `preco` decimal(10,2) NOT NULL,
  `data_atualizacao` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `codigo_barra` (`codigo_barra`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `produtos`
--

LOCK TABLES `produtos` WRITE;
/*!40000 ALTER TABLE `produtos` DISABLE KEYS */;
INSERT INTO `produtos` VALUES (2,'7896277200136','Chá de Camomila Madrugada','Chá de Camomila Madrugada, CX 10 saquinhos',2.49,'2026-10-16 22:10:59.895561'),(3,'12345670','Teste á é @ ñ ; \' \" , . : ´ ` { } [ ] ( ) < > _ - | \\ / ! # $ %','Teste á é @ ñ ; \' \" , . : ´ ` { } [ ] ( ) < > _ - | \\ / ! # $ %',1.99,'2026-10-16 22:11:10.350879'),(4,'7896328210756','Chá de Camomila Gengibre e Mel Barão','Chá misto de Camomila Gengibre e Mel.\r\nBarão de Cotegipe.',2.99,'2026-10-16 22:19:58.127546'),(21,'7896079431158','Arroz branco Namorado 5kg Tipo 1','Arroz branco Namorado 5kg Tipo 1',24.99,'2026-11-03 23:00:30.087844'),(25,'7894900029628','Coca Zero açúcar Zero Cafeína','Coca-Cola Zero Cafeína, Zero açúcar.',6.59,'2026-11-09 22:07:41.223457');
/*!40000 ALTER TABLE `produtos` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'verificador_precos'
--

--
-- Dumping routines for database 'verificador_precos'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-13 12:46:54
