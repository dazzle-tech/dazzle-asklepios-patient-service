package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.AddressLocation;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.CountryName;
import org.springframework.stereotype.Component;

@Component
public class WaseelCountryMapper {

    public AddressLocation.Country mapAlpha3ToCountry(String alpha3Code) {
        if (isBlank(alpha3Code)) {
            return null;
        }

        CountryName countryName = mapAlpha3ToEnum(alpha3Code);

        if (countryName == null) {
            return new AddressLocation.Country(
                    null,
                    alpha3Code.trim(),
                    alpha3Code.trim().toUpperCase()
            );
        }

        return new AddressLocation.Country(
                null,
                toDisplayName(countryName),
                alpha3Code.trim().toUpperCase()
        );
    }

    private CountryName mapAlpha3ToEnum(String alpha3Code) {
        return switch (alpha3Code.trim().toUpperCase()) {
            case "AFG" -> CountryName.AFGHANISTAN;
            case "ALB" -> CountryName.ALBANIA;
            case "DZA" -> CountryName.ALGERIA;
            case "ASM" -> CountryName.AMERICAN_SAMOA;
            case "AND" -> CountryName.ANDORRA;
            case "AGO" -> CountryName.ANGOLA;
            case "AIA" -> CountryName.ANGUILLA;
            case "ATA" -> CountryName.ANTARCTICA;
            case "ATG" -> CountryName.ANTIGUA_AND_BARBUDA;
            case "ARG" -> CountryName.ARGENTINA;
            case "ARM" -> CountryName.ARMENIA;
            case "ABW" -> CountryName.ARUBA;
            case "AUS" -> CountryName.AUSTRALIA;
            case "AUT" -> CountryName.AUSTRIA;
            case "AZE" -> CountryName.AZERBAIJAN;
            case "BHS" -> CountryName.BAHAMAS;
            case "BHR" -> CountryName.BAHRAIN;
            case "BGD" -> CountryName.BANGLADESH;
            case "BRB" -> CountryName.BARBADOS;
            case "BLR" -> CountryName.BELARUS;
            case "BEL" -> CountryName.BELGIUM;
            case "BLZ" -> CountryName.BELIZE;
            case "BEN" -> CountryName.BENIN;
            case "BMU" -> CountryName.BERMUDA;
            case "BTN" -> CountryName.BHUTAN;
            case "BOL" -> CountryName.BOLIVIA;
            case "BES" -> CountryName.BONAIRE;
            case "BIH" -> CountryName.BOSNIA_AND_HERZEGOVINA;
            case "BWA" -> CountryName.BOTSWANA;
            case "BVT" -> CountryName.BOUVET_ISLAND;
            case "BRA" -> CountryName.BRAZIL;
            case "IOT" -> CountryName.BRITISH_INDIAN_OCEAN_TERRITORY;
            case "BRN" -> CountryName.BRUNEI_DARUSSALAM;
            case "BGR" -> CountryName.BULGARIA;
            case "BFA" -> CountryName.BURKINA_FASO;
            case "BDI" -> CountryName.BURUNDI;
            case "CPV" -> CountryName.CAPE_VERDE;
            case "KHM" -> CountryName.CAMBODIA;
            case "CMR" -> CountryName.CAMEROON;
            case "CAN" -> CountryName.CANADA;
            case "CYM" -> CountryName.CAYMAN_ISLANDS;
            case "CAF" -> CountryName.CENTRAL_AFRICAN_REPUBLIC;
            case "TCD" -> CountryName.CHAD;
            case "CHL" -> CountryName.CHILE;
            case "CHN" -> CountryName.CHINA;
            case "CXR" -> CountryName.CHRISTMAS_ISLAND;
            case "CCK" -> CountryName.COCOS_ISLANDS;
            case "COL" -> CountryName.COLOMBIA;
            case "COM" -> CountryName.COMOROS;
            case "COD" -> CountryName.DR_CONGO;
            case "COG" -> CountryName.CONGO;
            case "COK" -> CountryName.COOK_ISLANDS;
            case "CRI" -> CountryName.COSTA_RICA;
            case "HRV" -> CountryName.CROATIA;
            case "CUB" -> CountryName.CUBA;
            case "CUW" -> CountryName.CURACAO;
            case "CYP" -> CountryName.CYPRUS;
            case "CZE" -> CountryName.CZECH_REPUBLIC;
            case "CIV" -> CountryName.COTE_DIVOIRE;
            case "DNK" -> CountryName.DENMARK;
            case "DJI" -> CountryName.DJIBOUTI;
            case "DMA" -> CountryName.DOMINICA;
            case "DOM" -> CountryName.DOMINICAN_REPUBLIC;
            case "ECU" -> CountryName.ECUADOR;
            case "EGY" -> CountryName.EGYPT;
            case "SLV" -> CountryName.EL_SALVADOR;
            case "GNQ" -> CountryName.EQUATORIAL_GUINEA;
            case "ERI" -> CountryName.ERITREA;
            case "EST" -> CountryName.ESTONIA;
            case "SWZ" -> CountryName.ESWATINI;
            case "ETH" -> CountryName.ETHIOPIA;
            case "FLK" -> CountryName.FALKLAND_ISLANDS;
            case "FRO" -> CountryName.FAROE_ISLANDS;
            case "FJI" -> CountryName.FIJI;
            case "FIN" -> CountryName.FINLAND;
            case "FRA" -> CountryName.FRANCE;
            case "GUF" -> CountryName.FRENCH_GUIANA;
            case "PYF" -> CountryName.FRENCH_POLYNESIA;
            case "GAB" -> CountryName.GABON;
            case "GMB" -> CountryName.GAMBIA;
            case "GEO" -> CountryName.GEORGIA;
            case "DEU" -> CountryName.GERMANY;
            case "GHA" -> CountryName.GHANA;
            case "GIB" -> CountryName.GIBRALTAR;
            case "GRC" -> CountryName.GREECE;
            case "GRL" -> CountryName.GREENLAND;
            case "GRD" -> CountryName.GRENADA;
            case "GLP" -> CountryName.GUADELOUPE;
            case "GUM" -> CountryName.GUAM;
            case "GTM" -> CountryName.GUATEMALA;
            case "GGY" -> CountryName.GUERNSEY;
            case "GIN" -> CountryName.GUINEA;
            case "GNB" -> CountryName.GUINEA_BISSAU;
            case "GUY" -> CountryName.GUYANA;
            case "HTI" -> CountryName.HAITI;
            case "HMD" -> CountryName.HEARD_ISLAND_AND_MCDONALD_ISLANDS;
            case "VAT" -> CountryName.VATICAN_CITY;
            case "HND" -> CountryName.HONDURAS;
            case "HKG" -> CountryName.HONG_KONG;
            case "HUN" -> CountryName.HUNGARY;
            case "ISL" -> CountryName.ICELAND;
            case "IND" -> CountryName.INDIA;
            case "IDN" -> CountryName.INDONESIA;
            case "IRN" -> CountryName.IRAN;
            case "IRQ" -> CountryName.IRAQ;
            case "IRL" -> CountryName.IRELAND;
            case "IMN" -> CountryName.ISLE_OF_MAN;
            case "ISR" -> CountryName.ISRAEL;
            case "ITA" -> CountryName.ITALY;
            case "JAM" -> CountryName.JAMAICA;
            case "JPN" -> CountryName.JAPAN;
            case "JEY" -> CountryName.JERSEY;
            case "JOR" -> CountryName.JORDAN;
            case "KAZ" -> CountryName.KAZAKHSTAN;
            case "KEN" -> CountryName.KENYA;
            case "KIR" -> CountryName.KIRIBATI;
            case "PRK" -> CountryName.NORTH_KOREA;
            case "KOR" -> CountryName.SOUTH_KOREA;
            case "KWT" -> CountryName.KUWAIT;
            case "KGZ" -> CountryName.KYRGYZSTAN;
            case "LAO" -> CountryName.LAOS;
            case "LVA" -> CountryName.LATVIA;
            case "LBN" -> CountryName.LEBANON;
            case "LSO" -> CountryName.LESOTHO;
            case "LBR" -> CountryName.LIBERIA;
            case "LBY" -> CountryName.LIBYA;
            case "LIE" -> CountryName.LIECHTENSTEIN;
            case "LTU" -> CountryName.LITHUANIA;
            case "LUX" -> CountryName.LUXEMBOURG;
            case "MAC" -> CountryName.MACAO;
            case "MDG" -> CountryName.MADAGASCAR;
            case "MWI" -> CountryName.MALAWI;
            case "MYS" -> CountryName.MALAYSIA;
            case "MDV" -> CountryName.MALDIVES;
            case "MLI" -> CountryName.MALI;
            case "MLT" -> CountryName.MALTA;
            case "MHL" -> CountryName.MARSHALL_ISLANDS;
            case "MTQ" -> CountryName.MARTINIQUE;
            case "MRT" -> CountryName.MAURITANIA;
            case "MUS" -> CountryName.MAURITIUS;
            case "MYT" -> CountryName.MAYOTTE;
            case "MEX" -> CountryName.MEXICO;
            case "FSM" -> CountryName.MICRONESIA;
            case "MDA" -> CountryName.MOLDOVA;
            case "MCO" -> CountryName.MONACO;
            case "MNG" -> CountryName.MONGOLIA;
            case "MNE" -> CountryName.MONTENEGRO;
            case "MSR" -> CountryName.MONTSERRAT;
            case "MAR" -> CountryName.MOROCCO;
            case "MOZ" -> CountryName.MOZAMBIQUE;
            case "MMR" -> CountryName.BURMA;
            case "NAM" -> CountryName.NAMIBIA;
            case "NRU" -> CountryName.NAURU;
            case "NPL" -> CountryName.NEPAL;
            case "NLD" -> CountryName.NETHERLANDS;
            case "NCL" -> CountryName.NEW_CALEDONIA;
            case "NZL" -> CountryName.NEW_ZEALAND;
            case "NIC" -> CountryName.NICARAGUA;
            case "NER" -> CountryName.NIGER;
            case "NGA" -> CountryName.NIGERIA;
            case "NIU" -> CountryName.NIUE;
            case "NFK" -> CountryName.NORFOLK_ISLAND;
            case "MNP" -> CountryName.NORTHERN_MARIANA_ISLANDS;
            case "NOR" -> CountryName.NORWAY;
            case "OMN" -> CountryName.OMAN;
            case "PAK" -> CountryName.PAKISTAN;
            case "PLW" -> CountryName.PALAU;
            case "PSE" -> CountryName.PALESTINE;
            case "PAN" -> CountryName.PANAMA;
            case "PNG" -> CountryName.PAPUA_NEW_GUINEA;
            case "PRY" -> CountryName.PARAGUAY;
            case "PER" -> CountryName.PERU;
            case "PHL" -> CountryName.PHILIPPINES;
            case "PCN" -> CountryName.PITCAIRN_ISLAND;
            case "POL" -> CountryName.POLAND;
            case "PRT" -> CountryName.PORTUGAL;
            case "PRI" -> CountryName.PUERTO_RICO;
            case "QAT" -> CountryName.QATAR;
            case "MKD" -> CountryName.NORTH_MACEDONIA;
            case "ROU" -> CountryName.ROMANIA;
            case "RUS" -> CountryName.RUSSIA;
            case "RWA" -> CountryName.RWANDA;
            case "REU" -> CountryName.REUNION;
            case "BLM" -> CountryName.SAINT_BARTHELEMY;
            case "SHN" -> CountryName.SAINT_HELENA;
            case "KNA" -> CountryName.SAINT_KITTS_AND_NEVIS;
            case "LCA" -> CountryName.SAINT_LUCIA;
            case "MAF" -> CountryName.SAINT_MARTIN;
            case "SPM" -> CountryName.SAINT_PIERRE_AND_MIQUELON;
            case "VCT" -> CountryName.SAINT_VINCENT_AND_THE_GRENADINES;
            case "WSM" -> CountryName.SAMOA;
            case "SMR" -> CountryName.SAN_MARINO;
            case "STP" -> CountryName.SAO_TOME_AND_PRINCIPE;
            case "SAU" -> CountryName.SAUDI_ARABIA;
            case "SEN" -> CountryName.SENEGAL;
            case "SRB" -> CountryName.SERBIA;
            case "SYC" -> CountryName.SEYCHELLES;
            case "SLE" -> CountryName.SIERRA_LEONE;
            case "SGP" -> CountryName.SINGAPORE;
            case "SXM" -> CountryName.SINT_MAARTEN;
            case "SVK" -> CountryName.SLOVAKIA;
            case "SVN" -> CountryName.SLOVENIA;
            case "SLB" -> CountryName.SOLOMON_ISLANDS;
            case "SOM" -> CountryName.SOMALIA;
            case "ZAF" -> CountryName.SOUTH_AFRICA;
            case "SGS" -> CountryName.SOUTH_GEORGIA_AND_SOUTH_SANDWICH_ISLANDS;
            case "SSD" -> CountryName.SOUTH_SUDAN;
            case "ESP" -> CountryName.SPAIN;
            case "LKA" -> CountryName.SRI_LANKA;
            case "SDN" -> CountryName.SUDAN;
            case "SUR" -> CountryName.SURINAME;
            case "SJM" -> CountryName.SVALBARD_AND_JAN_MAYEN;
            case "SWE" -> CountryName.SWEDEN;
            case "CHE" -> CountryName.SWITZERLAND;
            case "SYR" -> CountryName.SYRIA;
            case "TWN" -> CountryName.TAIWAN;
            case "TJK" -> CountryName.TAJIKISTAN;
            case "TZA" -> CountryName.TANZANIA;
            case "THA" -> CountryName.THAILAND;
            case "TLS" -> CountryName.TIMOR_LESTE;
            case "TGO" -> CountryName.TOGO;
            case "TKL" -> CountryName.TOKELAU;
            case "TON" -> CountryName.TONGA;
            case "TTO" -> CountryName.TRINIDAD_AND_TOBAGO;
            case "TUN" -> CountryName.TUNISIA;
            case "TUR" -> CountryName.TURKEY;
            case "TKM" -> CountryName.TURKMENISTAN;
            case "TCA" -> CountryName.TURKS_AND_CAICOS_ISLANDS;
            case "TUV" -> CountryName.TUVALU;
            case "UGA" -> CountryName.UGANDA;
            case "UKR" -> CountryName.UKRAINE;
            case "ARE" -> CountryName.UNITED_ARAB_EMIRATES;
            case "GBR" -> CountryName.UNITED_KINGDOM;
            case "UMI" -> CountryName.UNITED_STATES_MINOR_OUTLYING_ISLANDS;
            case "USA" -> CountryName.UNITED_STATES_OF_AMERICA;
            case "URY" -> CountryName.URUGUAY;
            case "UZB" -> CountryName.UZBEKISTAN;
            case "VUT" -> CountryName.VANUATU;
            case "VEN" -> CountryName.VENEZUELA;
            case "VNM" -> CountryName.VIETNAM;
            case "VGB" -> CountryName.BRITISH_VIRGIN_ISLANDS;
            case "VIR" -> CountryName.VIRGIN_ISLANDS_US;
            case "WLF" -> CountryName.WALLIS_AND_FUTUNA_ISLANDS;
            case "ESH" -> CountryName.WESTERN_SAHARA;
            case "YEM" -> CountryName.YEMEN;
            case "ZMB" -> CountryName.ZAMBIA;
            case "ZWE" -> CountryName.ZIMBABWE;
            default -> null;
        };
    }

    private String toDisplayName(CountryName countryName) {
        String text = countryName.name().toLowerCase().replace("_", " ");
        String[] parts = text.split(" ");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (!part.isBlank()) {
                result.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1))
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}