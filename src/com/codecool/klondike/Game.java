package com.codecool.klondike;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableArray;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Pane;

import javax.sound.midi.Soundbank;
import java.sql.SQLOutput;
import java.util.*;

import static com.codecool.klondike.Pile.PileType.FOUNDATION;
import static com.codecool.klondike.Pile.PileType.TABLEAU;

public class Game extends Pane {

    private List<Card> deck = new ArrayList<>();

    private Pile stockPile;
    private Pile discardPile;
    private List<Pile> foundationPiles = FXCollections.observableArrayList();
    private List<Pile> tableauPiles = FXCollections.observableArrayList();

    private double dragStartX, dragStartY;
    private List<Card> draggedCards = FXCollections.observableArrayList();

    private static double STOCK_GAP = 1;
    private static double FOUNDATION_GAP = 0;
    private static double TABLEAU_GAP = 30;


    private EventHandler<MouseEvent> onMouseClickedHandler = e -> {
        Card card = (Card) e.getSource();
        Pile containingPile = card.getContainingPile();
        if (containingPile.getPileType() == Pile.PileType.STOCK && containingPile.getTopCard().equals(card)) {
            card.moveToPile(discardPile);
            card.flip();
            card.setMouseTransparent(false);
            System.out.println("Placed " + card + " to the waste.");
        }
    };

    private EventHandler<MouseEvent> stockReverseCardsHandler = e -> {
        refillStockFromDiscard();
    };

    private EventHandler<MouseEvent> onMousePressedHandler = e -> {
        dragStartX = e.getSceneX();
        dragStartY = e.getSceneY();
    };

    private EventHandler<MouseEvent> onMouseDraggedHandler = e -> {
        Card card = (Card) e.getSource();
        Pile activePile = card.getContainingPile();
        if (activePile.getPileType() == Pile.PileType.STOCK)
            return;

        int draggedCardIndex = activePile.getCards().indexOf(card);

        draggedCards.clear();
        if (isCardValid(card, activePile)) {
            for (int i = draggedCardIndex; i < activePile.getCards().size(); i++) {

                double offsetX = e.getSceneX() - dragStartX;
                double offsetY = e.getSceneY() - dragStartY;

                card = activePile.getCards().get(i);
                draggedCards.add(card);

                card.getDropShadow().setRadius(20);
                card.getDropShadow().setOffsetX(10);
                card.getDropShadow().setOffsetY(10);

                card.toFront();
                card.setTranslateX(offsetX);
                card.setTranslateY(offsetY);
            }
        }
    };

    private EventHandler<MouseEvent> onMouseReleasedHandler = e -> {
        if (draggedCards.isEmpty())
            return;
        Card card = (Card) e.getSource();

        List<List> listOfPileLists = new ArrayList<>();
        listOfPileLists.add(0, tableauPiles);
        listOfPileLists.add(1, foundationPiles);

        Pile pile = null;
        for (List list : listOfPileLists) {
            pile = getValidIntersectingPile(card, list);
            if (pile != null) {
                break;
            }
        }
        if (pile != null) {
            handleValidMove(card, pile);
        } else {
            draggedCards.forEach(MouseUtil::slideBack);
            draggedCards.clear();
        }
    };

    public boolean isGameWon() {
        int counter = 0;
        for (Pile pile : foundationPiles) {
            if (pile.getTopCard() != null && pile.getTopCard().getRank() == 13) {
                counter += 1;
            }
        }
        if (counter == 3) {
            return true;
        }
        return false;
    }

    public Game() {
        deck = Card.createNewDeck();
        initPiles();
        dealCards();
        createButton();
    }

    public void addMouseEventHandlers(Card card) {
        card.setOnMousePressed(onMousePressedHandler);
        card.setOnMouseDragged(onMouseDraggedHandler);
        card.setOnMouseReleased(onMouseReleasedHandler);
        card.setOnMouseClicked(onMouseClickedHandler);
    }

    public void refillStockFromDiscard() {
        for (Card card : deck) {
            if (card.getContainingPile().getPileType() == Pile.PileType.DISCARD) {
                card.moveToPile(stockPile);
                card.flip();
                card.setMouseTransparent(false);
            }
        }
        System.out.println("Stock refilled from discard pile.");
    }

    public boolean isMoveValid(Card card, Pile destPile) {
        boolean allowed = false;
        if (destPile.getPileType() == TABLEAU) {
            if (destPile.getTopCard() == null && card.getRank() == 13) {
                allowed = true;
            }
            if (destPile.getTopCard() != null) {
                if (card.isOppositeColor(card, destPile.getTopCard()) && card.getRank() == destPile.getTopCard().getRank() - 1) {
                    allowed = true;
                } else {
                    allowed = false;
                }
            }
        }
        if (destPile.getPileType() == FOUNDATION) {
            if (destPile.getTopCard() == null && card.getRank() == 1) {
                allowed = true;
            }
            if (destPile.getTopCard() != null) {
                if (card.getSuit() == destPile.getTopCard().getSuit() && card.getRank() == destPile.getTopCard().getRank() + 1) {
                    allowed = true;
                } else {
                    allowed = false;
                }
            }
        }
        return allowed;
    }

    private Pile getValidIntersectingPile(Card card, List<Pile> piles) {
        Pile result = null;
        for (Pile pile : piles) {
            if (!pile.equals(card.getContainingPile()) &&
                    isOverPile(card, pile) &&
                    isMoveValid(card, pile))
                result = pile;
        }
        return result;
    }

    private boolean isOverPile(Card card, Pile pile) {
        if (pile.isEmpty())
            return card.getBoundsInParent().intersects(pile.getBoundsInParent());
        else
            return card.getBoundsInParent().intersects(pile.getTopCard().getBoundsInParent());
    }

    private void handleValidMove(Card card, Pile destPile) {
        String msg = null;
        if (destPile.isEmpty()) {
            if (destPile.getPileType().equals(Pile.PileType.FOUNDATION))
                msg = String.format("Placed %s to the foundation.", card);
            if (destPile.getPileType().equals(Pile.PileType.TABLEAU))
                msg = String.format("Placed %s to a new pile.", card);
        } else {
            msg = String.format("Placed %s to %s.", card, destPile.getTopCard());
        }
        System.out.println(msg);

        autoCardFlip(card);
        MouseUtil.slideToDest(draggedCards, destPile);
        draggedCards.clear();

        if (destPile.getPileType().equals(Pile.PileType.FOUNDATION) && isGameWon()) {
            alertWin();
        }
    }

    private void alertWin() {
        ButtonType yes = new ButtonType("YES", ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType("NO", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.WARNING,
                "WINNER WINNER CHICKEN DINNER!\n\nWant to play again?\n\nPress YES to play!\nPress NO to quit.",
                yes,
                no);
        alert.setTitle("YOU WON.");
        alert.setHeaderText(null);
        ImageView icon = new ImageView("alert/leo.gif");
        icon.setFitHeight(250);
        icon.setFitWidth(400);

        alert.getDialogPane().setGraphic(icon);

        Optional<ButtonType> result = alert.showAndWait();


        if (result.get() == yes){
            restartGame();
        } else if (result.get() == no){
            Platform.exit();
        }
    }

    private void autoCardFlip(Card card) {
        Pile current = card.getContainingPile();
        int flippingCardIndex = current.numOfCards() - draggedCards.size() - 1;
        if (current.getPileType() != Pile.PileType.DISCARD  && flippingCardIndex >= 0) {
            if (current.getCards().get(flippingCardIndex).isFaceDown()) {
                current.getCards().get(flippingCardIndex).flip();
            }
        }
    }

    private void initPiles() {
        stockPile = new Pile(Pile.PileType.STOCK, "Stock", STOCK_GAP);
        stockPile.setBlurredBackground();
        stockPile.setLayoutX(95);
        stockPile.setLayoutY(20);
        stockPile.setOnMouseClicked(stockReverseCardsHandler);
        getChildren().add(stockPile);

        discardPile = new Pile(Pile.PileType.DISCARD, "Discard", STOCK_GAP);
        discardPile.setBlurredBackground();
        discardPile.setLayoutX(285);
        discardPile.setLayoutY(20);
        getChildren().add(discardPile);

        for (int i = 0; i < 4; i++) {
            Pile foundationPile = new Pile(Pile.PileType.FOUNDATION, "Foundation " + i, FOUNDATION_GAP);
            foundationPile.setBlurredBackground();
            foundationPile.setLayoutX(610 + i * 180);
            foundationPile.setLayoutY(20);
            foundationPiles.add(foundationPile);
            getChildren().add(foundationPile);
        }
        for (int i = 0; i < 7; i++) {
            Pile tableauPile = new Pile(Pile.PileType.TABLEAU, "Tableau " + i, TABLEAU_GAP);
            tableauPile.setBlurredBackground();
            tableauPile.setLayoutX(95 + i * 180);
            tableauPile.setLayoutY(275);
            tableauPiles.add(tableauPile);
            getChildren().add(tableauPile);
        }
    }

    public void dealCards() {
        Iterator<Card> deckIterator = deck.iterator();

        int nextCardIndex = 0;
        int j;
        for (int i = 0; i < tableauPiles.size(); i++) {
            for (j = nextCardIndex; j < nextCardIndex + i + 1; j++) {
                Card card = deckIterator.next();
                tableauPiles.get(i).addCard(card);
                addMouseEventHandlers(card);
                getChildren().add(card);
            }
            tableauPiles.get(i).getTopCard().flip();
            nextCardIndex = j;
        }

        deckIterator.forEachRemaining(card -> {
            stockPile.addCard(card);
            addMouseEventHandlers(card);
            getChildren().add(card);
        });
    }

    public void setTableBackground(Image tableBackground) {
        setBackground(new Background(new BackgroundImage(tableBackground,
                BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT,
                BackgroundPosition.CENTER, BackgroundSize.DEFAULT)));
    }

    public boolean isCardValid(Card card, Pile pile) {
        Pile.PileType pileType = pile.getPileType();
        Card topCard = pile.getTopCard();

        if (pileType == Pile.PileType.DISCARD && topCard.equals(card)) {
            return true;
        } else if (pileType == Pile.PileType.FOUNDATION && topCard.equals(card)) {
            return true;
        } else if (pileType == Pile.PileType.TABLEAU && !card.isFaceDown()) {
            return true;
        } else {
            return false;
        }
    }

    public void createButton() {
            Button restartButton = new Button("RESTART");
            getChildren().add(restartButton);
            restartButton.setOnMouseClicked(event -> {
                restartGame();
            });
    }

    public void restartGame(){
        tableauPiles.clear();
        foundationPiles.clear();
        stockPile.clear();
        discardPile.clear();
        deck.clear();
        getChildren().clear();

        deck = Card.createNewDeck();
        initPiles();
        dealCards();
        createButton();
    }
}
