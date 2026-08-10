$(function () {
    function renderCard(result) {
        var $card = $($('#search-result-template').prop('content')).find('.col-md-6').clone();
        $card.find('.result-document-name').text(result.documentName);
        $card.find('.result-score').text(result.score.toFixed(2));
        $card.find('.result-chunk-text').text(result.chunkText);
        return $card;
    }

    $('#search-form').on('submit', function (event) {
        event.preventDefault();

        var question = $('#question').val();
        var $feedback = $('#search-feedback').addClass('d-none');
        var $results = $('#search-results').empty();
        var $submit = $('#search-submit');
        $submit.prop('disabled', true);

        $.ajax({
            url: '/api/search',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({question: question})
        }).done(function (results) {
            results.forEach(function (result) {
                $results.append(renderCard(result));
            });
            if (!results.length) {
                $feedback.removeClass('d-none alert-danger').addClass('alert-info').text('Nenhum resultado encontrado.');
            }
        }).fail(function (xhr) {
            var message = (xhr.responseJSON && xhr.responseJSON.message) ? xhr.responseJSON.message : 'Falha ao buscar.';
            $feedback.removeClass('d-none alert-info').addClass('alert-danger').text(message);
        }).always(function () {
            $submit.prop('disabled', false);
        });
    });
});
