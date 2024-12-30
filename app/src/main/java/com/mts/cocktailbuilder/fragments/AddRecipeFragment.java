package com.mts.cocktailbuilder.fragments;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.mts.cocktailbuilder.R;
import com.mts.cocktailbuilder.configuration.CocktailBuilderDBHelper;
import com.mts.cocktailbuilder.configuration.CocktailBuilderDatabase;
import com.mts.cocktailbuilder.mybar.AddBarItemFragment;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class AddRecipeFragment extends DialogFragment {

    private Context mContext;
    private EditText recipeTitle;
    private EditText methodText;
    private RatingBar ratingBar;
    private Button editRecipeButton;
    private LinearLayout parentIngredientLayout;
    private LinearLayout placeholder;
    private View buttonView;
    private EditText addQuantity;
    private AutoCompleteTextView autoCompleteTextView;
    private ArrayAdapter<String> autoCompleteAdapter;
    private int numIngredients = 0;
    private Map<String, Integer> ingredientIDs = new HashMap<>();
    private Map<String, Float> ingredientAmounts = new HashMap<>();
    private String[] ingredientList;
    private AddRecipeInterface mAddItemHandler;
    private boolean additionalIngredientButton = false;


    private SQLiteDatabase database;
    private CocktailBuilderDBHelper dbHelper;

    @Override
    public void onAttach(Context context){
        super.onAttach(context);
        mContext = context;
    }

    public AddRecipeFragment(AddRecipeInterface AddRecipeHandler) {
        mAddItemHandler = AddRecipeHandler;
    }


    public interface AddRecipeInterface {
        void onAddRecipeClick();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Holo_Light);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_add_recipe, container);
        editRecipeButton = v.findViewById(R.id.add_recipe_fragment_button);
        editRecipeButton.setOnClickListener(v1 -> {
            addRecipe();
        });
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recipeTitle = view.findViewById(R.id.add_recipe_title);
        recipeTitle.requestFocus();
        methodText = view.findViewById(R.id.add_method);
        ratingBar = view.findViewById(R.id.add_recipe_ratingbar);

        parentIngredientLayout = view.findViewById(R.id.ingredients_list);
        addQuantity = view.findViewById(R.id.add_quantity);
        autoCompleteTextView = view.findViewById(R.id.autocomplete);

        placeholder = view.findViewById(R.id.additional_ingredient_button_placeholder);

        dbHelper = new CocktailBuilderDBHelper(getActivity());
        database = dbHelper.getWritableDatabase();

        setUpAutoComplete(); //Function used to set and update autocomplete
        autoCompleteAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.select_dialog_item, ingredientList);
        autoCompleteTextView.setAdapter(autoCompleteAdapter);
        autoCompleteTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 0 || (autoCompleteAdapter.getCount() != 0 && placeholder.getChildCount() != 0)) {
                    removeButton(buttonView);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (autoCompleteAdapter.getCount() == 0 && !additionalIngredientButton){
                    Toast.makeText(mContext, "No results found", Toast.LENGTH_SHORT).show();
                    addAdditionalIngredientButton(view, s);
                }

            }
        });


        addQuantity.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
            @Override
            public void afterTextChanged(Editable s) {
                if(s.toString().trim().length() != 0 && Float.parseFloat(s.toString()) > 0.1 && !s.toString().trim().equals(".")){
                    try{
                        String name = autoCompleteTextView.getText().toString().trim();
                        if (ingredientAmounts.get(name) != null){
                            Toast toast = Toast.makeText(mContext, "Cannot input the same ingredient twice", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,0);
                            toast.show();
                            autoCompleteTextView.setText("");
                            addQuantity.setText("");
                            return;
                        }
                        int id = ingredientIDs.get(name);
                        String quantity = addQuantity.getText().toString();
                        addIngredientField(name, quantity);
                        autoCompleteTextView.setText("");
                        addQuantity.setText("");

                    }catch (NullPointerException e){
                        Log.e("addRecipeFrag", "No such ingredient found", e);
                        String msg = "Please select from the drop down menu, or first add ingredient";
                        Toast toast = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,0);
                        toast.show();
                        autoCompleteTextView.setText("");
                        addQuantity.setText("");
                    }

                }
            }
        });

    }


    public void addRecipe() {
        String recipeName = recipeTitle.getText().toString().toLowerCase().trim();
        String method = methodText.getText().toString().toLowerCase().trim();
        boolean recipeDataSuccess;
        boolean relationDataSuccess = true;
        int recipeID;


        if (recipeName.length() == 0) {
            Toast.makeText(getDialog().getContext(), "Recipe must have a title", Toast.LENGTH_LONG).show();
            return;
        } else {
            ContentValues cvRecipe = new ContentValues();
            cvRecipe.put(CocktailBuilderDatabase.RecipeEntry.COLUMN_NAME, recipeName);
            cvRecipe.put(CocktailBuilderDatabase.RecipeEntry.COLUMN_METHOD, method);
            if (ratingBar.getRating() > 0.5) {
                float ratingBarValue = ratingBar.getRating();
                cvRecipe.put(CocktailBuilderDatabase.RecipeEntry.COLUMN_RATING, ratingBarValue);
            }

            try {
                database.insert(CocktailBuilderDatabase.RecipeEntry.TABLE_NAME, null, cvRecipe);
                recipeDataSuccess = true;
                this.mAddItemHandler.onAddRecipeClick();
            } catch (Error e) {
                CharSequence SQLError = "Error adding item to Recipe database";
                Toast.makeText(getDialog().getContext(), SQLError, Toast.LENGTH_SHORT).show();
                Log.e("myRecipeAdd", "SQL error");
                recipeDataSuccess = false;
            }


        }
        if (numIngredients < 2) {
            Toast.makeText(getDialog().getContext(), "Recipe must have at least two ingredients", Toast.LENGTH_SHORT).show();
            return;
        } else if (!recipeDataSuccess){
            Toast.makeText(getDialog().getContext(), "Recipe database error", Toast.LENGTH_SHORT).show();
            return;
        }
        else {
            recipeID = getRecipeID();
            if(recipeID == -1){
                Toast.makeText(getContext(),"Database error", Toast.LENGTH_SHORT).show();
                return;
            }
            ContentValues cvIngredients = new ContentValues();
            String[] ingredients = ingredientAmounts.keySet().toArray(new String[ingredientAmounts.size()]);
            for (int i = 0; i < numIngredients; i++) {
                int ingredientID = ingredientIDs.get(ingredients[i]);
                float amount = ingredientAmounts.get(ingredients[i]);
                cvIngredients.put(CocktailBuilderDatabase.IngredientRelation.COLUMN_INGREDIENTID, ingredientID);
                cvIngredients.put(CocktailBuilderDatabase.IngredientRelation.COLUMN_AMOUNT, amount);
                cvIngredients.put(CocktailBuilderDatabase.IngredientRelation.COLUMN_RECIPEID, recipeID);
                try {
                    database.insert(CocktailBuilderDatabase.IngredientRelation.TABLE_NAME,null, cvIngredients);
                }catch (Error e){
                    CharSequence SQLError = "Error adding item to Relational database";
                    Toast.makeText(getDialog().getContext(), SQLError, Toast.LENGTH_SHORT).show();
                    Log.e("myRecipeAdd", "SQL error");
                    relationDataSuccess = false;
                }
            }
            mAddItemHandler.onAddRecipeClick();
            database.close();

        }
        if (relationDataSuccess) {
            FullListFragment vp = (FullListFragment) AddRecipeFragment.this.getParentFragment();
            vp.updateList();
            getDialog().dismiss();
        }
    }

    public int getRecipeID() {
        Cursor cursor = database.query(
                CocktailBuilderDatabase.RecipeEntry.TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                null

        );

        if (cursor.moveToFirst()) {
            cursor.moveToLast();
            return Integer.parseInt(cursor.getString(cursor.getColumnIndex(CocktailBuilderDatabase.RecipeEntry._ID)));
        } else {
            return -1;
        }
    }

    protected Cursor loadIngredientList(){
        String[] cols = {
                CocktailBuilderDatabase.DrinkEntry.COLUMN_BRAND,
                CocktailBuilderDatabase.DrinkEntry.COLUMN_VARIANT,
                CocktailBuilderDatabase.DrinkEntry.COLUMN_TYPE,
                CocktailBuilderDatabase.DrinkEntry._ID
        };
        return database.query(
                CocktailBuilderDatabase.DrinkEntry.TABLE_NAME,
                cols,
                null,
                null,
                null,
                null,
                null
        );
    }


    public void setUpAutoComplete(){
        ingredientIDs.clear();
        Cursor mCursor = loadIngredientList();
        while (mCursor.moveToNext()){
            String name = mCursor.getString(mCursor.getColumnIndex(CocktailBuilderDatabase.DrinkEntry.COLUMN_BRAND))
                    + " " + mCursor.getString(mCursor.getColumnIndex(CocktailBuilderDatabase.DrinkEntry.COLUMN_VARIANT))
                    + " " + mCursor.getString(mCursor.getColumnIndex(CocktailBuilderDatabase.DrinkEntry.COLUMN_TYPE));
            int id = mCursor.getInt(mCursor.getColumnIndex(CocktailBuilderDatabase.DrinkEntry._ID));
            ingredientIDs.put(name.trim(),id);
        }

        ingredientList = ingredientIDs.keySet().toArray(new String[ingredientIDs.size()]);

    }




    public void addIngredientField( String name, String quantity){
        ingredientAmounts.put(name, Float.parseFloat(quantity));
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.recipe_ingredient_field, null);
        TextView nameText = rowView.findViewById(R.id.ingredient_name);
        nameText.setTag("ingredient name");
        TextView quantityText = rowView.findViewById(R.id.ingredient_quantity);
        nameText.setText(String.format("%s. %s",numIngredients+1, name.trim()));
        float quant = Float.parseFloat(quantity);
        if (quant < 0.22){
            quantityText.setText(R.string.splash);
        }
        else{
            quantity = String.format(Locale.UK, "%.1f", quant);
            quantityText.setText(quantity);
        }
        parentIngredientLayout.addView(rowView, parentIngredientLayout.getChildCount());
        numIngredients = numIngredients + 1;
        rowView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openDeleteDialog(v);
                return false;
            }
        });

    }

    public void addAdditionalIngredientButton(View view, CharSequence s){
        if(additionalIngredientButton || s.length() == 0){
            return;
        }
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        buttonView = inflater.inflate(R.layout.additional_ingredient_button_layout, null);
        Button button = buttonView.findViewById(R.id.additional_ingredient_button);
        placeholder.addView(buttonView, placeholder.getChildCount());

        button.setOnClickListener(v -> {
            FragmentManager fm = getChildFragmentManager();
            AddBarItemFragment addBarItemFragment = new AddBarItemFragment();
            addBarItemFragment.show(fm,"add_new_ingredient");

            fm.executePendingTransactions();
            addBarItemFragment.getDialog().setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    setUpAutoComplete();
                    autoCompleteAdapter.clear();
                    autoCompleteAdapter.addAll(ingredientList);
                    autoCompleteAdapter.notifyDataSetChanged();
                    autoCompleteTextView.setAdapter(autoCompleteAdapter);
                }
            });
            autoCompleteTextView.setText("");
        });
        additionalIngredientButton = true;
    }

    public void removeButton(View view){
        placeholder.removeView(view);
        additionalIngredientButton = false;
    }


    public void deleteIngredientField(View view){
        String[] textView = ((TextView) view.findViewWithTag("ingredient name")).getText().toString().split("\\.");
        String ingredient = textView[1].trim();
        parentIngredientLayout.removeView(view);
        ingredientAmounts.remove(ingredient);
        numIngredients -= 1;
    }



    public void openDeleteDialog(View view) {
        // Dialog code to delete recipes
        View deleteRecipe = View.inflate(mContext, R.layout.delete_ingredient_dialog, null);

        AlertDialog.Builder deleteDialogBuilder = new AlertDialog.Builder(this.getContext());

        deleteDialogBuilder
                .setView(deleteRecipe)
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteIngredientField(view);
                    dialog.cancel();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        AlertDialog deleteDialog = deleteDialogBuilder.create();
        deleteDialog.getWindow().setBackgroundDrawableResource(R.color.white_blue);

        deleteDialog.show();
        Button deleteButton = deleteDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        deleteButton.setTextColor(Color.RED);

        Button cancelButton = deleteDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        cancelButton.setTextColor(Color.GRAY);

    }
}