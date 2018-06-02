package aplicaciones.sainz.jorge.restcontprov;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import org.dom4j.DocumentException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import aplicaciones.sainz.jorge.restcontprov.comunicaciones.ConexionesRS;
import aplicaciones.sainz.jorge.restcontprov.conversores.ConversorDouble;
import aplicaciones.sainz.jorge.restcontprov.conversores.ConversorFecha;
import aplicaciones.sainz.jorge.restcontprov.datos.Persona;
import aplicaciones.sainz.jorge.restcontprov.utilidades.Auth;
import aplicaciones.sainz.jorge.restcontprov.utilidades.XMLFormat;

import static aplicaciones.sainz.jorge.restcontprov.comunicaciones.JWT.createToken;
import static aplicaciones.sainz.jorge.restcontprov.comunicaciones.JWT.verifyToken;

/**
 * @author JJSC, 2018
 *
 * Clase ejemplo que realiza las siguientes operaciones:
 * - Consulta un RESTfull, bajando informacion de datos en formato XML.
 * - LLena una lista de objetos a partir de la lectura anterior.
 * - Inserta en un proveedor de contenido estos objetos.
 * - Consulta en un proveedor de contenido los datos de una tabla.
 */
public class Main extends AppCompatActivity implements View.OnClickListener {
    /**
     * Atributos usados para el tratamiento
     * del proveedor de contenido
     */
    private static final String URI;
    private Cursor cursor;
    private ContentResolver cr;
    private Uri clienteUri;
    private ContentValues valores;

    /**
     * Elementos del layout
     */
    private TextView resultado;
    private EditText url;

    /**
     *  Atributos para el tratamiento de strings y
     *  lista de objetos
     */
    private StringBuilder sb;
    private List<Persona> personas;
    private XStream xs;

    static {
        /**
         * Inicializacion del URI del content provider
         */
        URI = "content://org.app.mp.provider/personas";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /**
         * Inicializacion de todos los atributos
         */
        resultado = findViewById(R.id.resultado);
        resultado.setMovementMethod(new ScrollingMovementMethod());
        url = findViewById(R.id.url);

        findViewById(R.id.leer).setOnClickListener(this);
        findViewById(R.id.consultar).setOnClickListener(this);
        findViewById(R.id.insertar).setOnClickListener(this);

        personas = new ArrayList<>();
        sb = new StringBuilder();

        /*
          Inicializacion de los objetos para el trabajo con el
          proveedor de contenido.
         */
        cr = getContentResolver();
        clienteUri = Uri.parse(URI);
        valores = new ContentValues();
        cursor = null;

        inicializaXStream();
    }

    /**
     * Crea el objeto para el tratamiento del XML
     */
    private void inicializaXStream() {
        xs = new XStream(new DomDriver());
        xs.alias("personas", List.class);
        xs.alias("persona", Persona.class);
        xs.registerConverter(new ConversorDouble());
        xs.registerConverter(new ConversorFecha());
        xs.omitField(Persona.class, "personaId");
    }

    /**
     * Manipula las acciones de los botones
     * @param v
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.leer:
                new Hilo().execute();
                break;
            case R.id.consultar:
                new Hilo("consultarCP").execute();
                break;
            case R.id.insertar:
                new Hilo("insertarCP").execute();
                break;
            default:
        }

    }


    /**
     * Realiza todas las operaciones de los botones
     * en segundo plano
     *
     */
    @SuppressLint("StaticFieldLeak")
    private class Hilo extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog progreso;
        private StringBuilder errores;
        private Map<String, String> resultado;
        private String xml;

        private String operacion;

        Hilo() {
            operacion = "leerREST";
        }

        Hilo(String tipo) {
            this.operacion = tipo;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            hideKeyb(Main.this);

            errores = new StringBuilder();
            resultado = new HashMap<>();

            progreso = new ProgressDialog(Main.this);
            progreso.setCancelable(true);
            progreso.setMessage(getString(R.string.leyendo_espere));
            progreso.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    Hilo.this.cancel(true);
                }
            });
            progreso.show();
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            xml = "";
            publishProgress();
            try {
                /*
                  Lee el RESTfull
                 */
                if (operacion.equalsIgnoreCase("leerREST")) {
                    List<String> roles = new ArrayList();
                    roles.add("read");

                    String payload = new Gson().toJson(new Auth("admin", "admin", roles));
                    String secretKey = "e6K0v3I5s4B2l9G8";
                    String signatureAlg = "HS512";

                    String auth = createToken(secretKey, payload, signatureAlg);
                    if (verifyToken(secretKey, auth, signatureAlg)) {
                        resultado = ConexionesRS.connectREST(
                                url.getText().toString(),
                                "/personas_rs",
                                new HashMap<String, String>(),
                                "application/x-www-form-urlencoded;charset=UTF-8",
                                "application/xml",
                                "",
                                "GET",
                                auth,
                                10000,
                                10000);
                    }

                    if (isCancelled())  return false;

                    if (Integer.parseInt(resultado.get("code")) < 300) {
                        xml = resultado.get("body");
                    } else {
                        errores.append(resultado.get("message")).append("\n");
                        return false;
                    }
                }
                /*
                  Inserta en el proveedor de contenido los objetos que estan en
                  la lista y que ha sido llenada en la lectura del RESTfull
                 */
                if (operacion.equalsIgnoreCase("insertarCP")) {
                    sb.setLength(0);
                    if ((personas != null) && (personas.size() > 0)) {
                        for (Persona persona : personas) {
                            valores.clear();
                            valores.put("nombre", persona.getNombre());
                            valores.put("cedula", persona.getCedula());
                            valores.put("fecha_nacimiento", new java.sql.Date(persona.getFechaNacimiento().getTime()).toString());
                            valores.put("estado_civil", persona.getEstadoCivil());
                            valores.put("genero", persona.getGenero());
                            valores.put("estatura", persona.getEstatura().toString());
                            if (isCancelled())  return false;
                            try {
                                Uri newUri = cr.insert(clienteUri, valores);
                                assert newUri != null;
                                sb.append(newUri.toString());
                            } catch (NullPointerException e) {
                                sb.append("CEDULA ").append(persona.getCedula()).append(" existe");
                            }
                            sb.append("\n");
                        }
                    }
                    else {
                        errores.append("Lista vacia");
                        return false;
                    }
                }
                /*
                  Consulta todos los registros de la tabla persona perteneciente al content provider
                 */
                if (operacion.equalsIgnoreCase("consultarCP")) {
                    sb.setLength(0);
                    cursor = cr.query(clienteUri,
                            null,
                            null,
                            null,
                            null);

                    if (cursor != null) {
                        try {
                            cursor.moveToFirst();
                            do {
                                if (isCancelled())  return false;
                                DatabaseUtils.cursorRowToContentValues(cursor, valores);
                                sb.append(valores.toString()).append("\n\n");
                            } while (cursor.moveToNext());

                        } catch (CursorIndexOutOfBoundsException e) {
                            errores.append(e.toString());
                            return false;
                        }
                    }
                    else {
                        sb.append("No existen resultado\n");
                    }
                }
            } catch (NullPointerException | IOException e) {
                errores.append(e.toString()).append("\n");
                return false;
            }
            return true;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
            if ((Main.this.resultado.getText() != null)&&(!Main.this.resultado.getText().toString().isEmpty()) ) {
                Main.this.resultado.setText("");
            }
        }

        /**
         * Ejecuta las operaciones finales del proceso en segundo plano
         * @param aBoolean
         */
        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            if (!aBoolean) {
                alerta(Main.this, "ERROR", errores.toString());
            } else {
                if (operacion.equalsIgnoreCase("leerREST")) {
                    personas.clear();
                    personas.addAll( (List<Persona>) xs.fromXML(xml));
                    try {
                        Main.this.resultado.setText(XMLFormat.prettyFormat(xml));
                    } catch (IOException | DocumentException ignored) {
                    }
                }
                if (operacion.equalsIgnoreCase("insertarCP") ||
                        operacion.equalsIgnoreCase("consultarCP")) {
                    Main.this.resultado.setText(sb.toString());
                }
            }
            progreso.dismiss();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            progreso.dismiss();
            Toast.makeText(Main.this, R.string.operacion_cancelada, Toast.LENGTH_SHORT).show();
        }
    }

    public static void alerta(Context context, String titulo, String cadena) {
        new AlertDialog.Builder(context)
                .setMessage(cadena)
                .setCancelable(true).setTitle(titulo)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                }).create().show();
    }

    public static void hideKeyb(Activity act) {
        InputMethodManager inputManager = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputManager != null) {
            inputManager.hideSoftInputFromWindow(act.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }


}
